package kr.or.sportmap.community.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import kr.or.sportmap.community.domain.Comment;
import kr.or.sportmap.community.domain.Question;
import kr.or.sportmap.community.repository.CommentRepository;
import kr.or.sportmap.community.repository.NoticeRepository;
import kr.or.sportmap.community.repository.QuestionRepository;
import kr.or.sportmap.member.service.MemberService;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.reservation.domain.Reservation;
import kr.or.sportmap.reservation.repository.ReservationRepository;
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
  private final ReservationRepository reservations;

  public BoardController(
    NoticeRepository notices,
    QuestionRepository questions,
    CommentRepository comments,
    MemberService members,
    ReservationRepository reservations
  ) {
    this.notices = notices;
    this.questions = questions;
    this.comments = comments;
    this.members = members;
    this.reservations = reservations;
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
    Member author = members.findAuthenticated(jwt.getSubject());
    if (author.getRole() != Member.Role.ADMIN) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "관리자만 댓글을 작성할 수 있습니다"
    );
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
        author,
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

  @GetMapping("/users/me/notifications")
  public java.util.List<NotificationResponse> notifications(
    @AuthenticationPrincipal Jwt jwt
  ) {
    Member member = members.findAuthenticated(jwt.getSubject());
    var answers = questions
      .findTop20ByAuthorIdOrderByIdDesc(member.id)
      .stream()
      .map(question ->
        comments
          .findFirstByQuestionIdOrderByCreatedAtDesc(question.id)
          .map(answer ->
            new NotificationResponse(
              "ANSWER",
              question.id,
              question.title,
              answer.content,
              "답변 · " + answer.author.fullName,
              answer.createdAt,
              question.answerReadAt != null &&
              !question.answerReadAt.isBefore(answer.createdAt)
            )
          )
          .orElse(null)
      )
      .filter(java.util.Objects::nonNull)
      .toList();
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    Instant dayStart = LocalDate.now(seoul).atStartOfDay(seoul).toInstant();
    Instant dayEnd = LocalDate.now(seoul)
      .plusDays(1)
      .atStartOfDay(seoul)
      .toInstant();
    var starts = reservations
      .findStartNotifications(
        member.id,
        dayStart,
        dayEnd,
        Reservation.Status.CANCELLED
      )
      .stream()
      .map(reservation ->
        new NotificationResponse(
          "RESERVATION_START",
          reservation.id,
          reservation.program.name,
          "오늘 시작하는 예약 프로그램입니다.",
          "예약 일정",
          reservation.startsAt,
          reservation.startNotificationReadAt != null
        )
      )
      .toList();
    return java.util.stream.Stream
      .concat(answers.stream(), starts.stream())
      .sorted(Comparator.comparing(NotificationResponse::occurredAt).reversed())
      .toList();
  }

  @PatchMapping("/users/me/answer-notifications/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void readAnswerNotification(
    @PathVariable Long id,
    @AuthenticationPrincipal Jwt jwt
  ) {
    Member member = members.findAuthenticated(jwt.getSubject());
    Question question = questions
      .findByIdAndAuthorId(id, member.id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    question.answerReadAt = Instant.now();
    questions.save(question);
  }

  @PatchMapping("/users/me/reservation-notifications/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void readReservationNotification(
    @PathVariable Long id,
    @AuthenticationPrincipal Jwt jwt
  ) {
    Member member = members.findAuthenticated(jwt.getSubject());
    Reservation reservation = reservations
      .findByIdAndMemberId(id, member.id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    reservation.startNotificationReadAt = Instant.now();
    reservations.save(reservation);
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

  public record NotificationResponse(
    String type,
    Long targetId,
    String title,
    String preview,
    String source,
    Instant occurredAt,
    boolean read
  ) {}
}
