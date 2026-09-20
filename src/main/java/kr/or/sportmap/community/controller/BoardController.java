package kr.or.sportmap.community.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import kr.or.sportmap.community.domain.Comment;
import kr.or.sportmap.community.domain.Question;
import kr.or.sportmap.community.repository.CommentRepository;
import kr.or.sportmap.community.repository.NoticeRepository;
import kr.or.sportmap.community.repository.QuestionRepository;
import kr.or.sportmap.member.service.MemberService;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api")
public class BoardController {

  private final NoticeRepository notices;
  private final QuestionRepository questions;
  private final CommentRepository comments;
  private final MemberService members;

  public BoardController(
    NoticeRepository notices,
    QuestionRepository questions,
    CommentRepository comments,
    MemberService members
  ) {
    this.notices = notices;
    this.questions = questions;
    this.comments = comments;
    this.members = members;
  }

  @GetMapping("/notices")
  public Page<NoticeResponse> notices(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    return notices
      .findAll(paging(page, size))
      .map(n -> new NoticeResponse(n.id, n.title, n.content, n.createdAt));
  }

  @GetMapping("/notices/{id}")
  public NoticeResponse notice(@PathVariable Long id) {
    return notices
      .findById(id)
      .map(n -> new NoticeResponse(n.id, n.title, n.content, n.createdAt))
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  @GetMapping("/qna")
  public Page<QuestionResponse> questions(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    return questions
      .findAll(paging(page, size))
      .map(q -> new QuestionResponse(q.id, q.title, q.content, q.createdAt));
  }

  @GetMapping("/qna/{id}")
  public QuestionResponse question(@PathVariable Long id) {
    return questions
      .findById(id)
      .map(q -> new QuestionResponse(q.id, q.title, q.content, q.createdAt))
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  @PostMapping("/qna")
  @ResponseStatus(HttpStatus.CREATED)
  public QuestionResponse createQuestion(
    @Valid @RequestBody QuestionRequest request,
    @AuthenticationPrincipal Jwt jwt
  ) {
    Question q = questions.save(
      new Question(
        members.findAuthenticated(jwt.getSubject()),
        request.title().trim(),
        request.content().trim()
      )
    );
    return new QuestionResponse(q.id, q.title, q.content, q.createdAt);
  }

  @GetMapping("/qna/{id}/comments")
  public java.util.List<CommentResponse> comments(@PathVariable Long id) {
    if (!questions.existsById(id)) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND
    );
    return comments
      .findByQuestionIdOrderByCreatedAtAsc(id)
      .stream()
      .map(c ->
        new CommentResponse(
          c.id,
          c.parent == null ? null : c.parent.getId(),
          c.author.fullName,
          c.content,
          c.createdAt
        )
      )
      .toList();
  }

  @PostMapping("/qna/{id}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  public CommentResponse createComment(
    @PathVariable Long id,
    @Valid @RequestBody CommentRequest request,
    @AuthenticationPrincipal Jwt jwt
  ) {
    Question q = questions
      .findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    Comment parent = null;
    if (request.parentId() != null) {
      parent = comments
        .findById(request.parentId())
        .orElseThrow(() ->
          new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "parent comment not found"
          )
        );
      if (
        !parent.question.getId().equals(id)
      ) throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "parent belongs to another question"
      );
    }
    Comment c = comments.save(
      new Comment(
        q,
        members.findAuthenticated(jwt.getSubject()),
        parent,
        request.content().trim()
      )
    );
    return new CommentResponse(
      c.id,
      parent == null ? null : parent.getId(),
      c.author.fullName,
      c.content,
      c.createdAt
    );
  }

  private PageRequest paging(int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST
    );
    return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
  }

  public record NoticeResponse(
    Long id,
    String title,
    String content,
    Instant createdAt
  ) {}

  public record QuestionResponse(
    Long id,
    String title,
    String content,
    Instant createdAt
  ) {}

  public record QuestionRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 5000) String content
  ) {}

  public record CommentRequest(
    Long parentId,
    @NotBlank @Size(max = 3000) String content
  ) {}

  public record CommentResponse(
    Long id,
    Long parentId,
    String authorName,
    String content,
    Instant createdAt
  ) {}
}
