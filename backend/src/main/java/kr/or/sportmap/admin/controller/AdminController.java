package kr.or.sportmap.admin.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import kr.or.sportmap.community.domain.Notice;
import kr.or.sportmap.community.repository.NoticeRepository;
import kr.or.sportmap.facility.repository.FacilityRepository;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.member.repository.MemberRepository;
import kr.or.sportmap.member.service.MemberService;
import kr.or.sportmap.program.repository.ProgramRepository;
import kr.or.sportmap.reservation.repository.ReservationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** ADMIN JWT 권한이 있는 사용자만 접근할 수 있는 관리 API다. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final MemberRepository members;
  private final FacilityRepository facilities;
  private final ProgramRepository programs;
  private final ReservationRepository reservations;
  private final NoticeRepository notices;
  private final MemberService memberService;

  public AdminController(
    MemberRepository members,
    FacilityRepository facilities,
    ProgramRepository programs,
    ReservationRepository reservations,
    NoticeRepository notices,
    MemberService memberService
  ) {
    this.members = members;
    this.facilities = facilities;
    this.programs = programs;
    this.reservations = reservations;
    this.notices = notices;
    this.memberService = memberService;
  }

  /** 관리 화면의 회원·시설·프로그램·예약·공지 건수를 조회한다. */
  @GetMapping("/overview")
  public Overview overview() {
    return new Overview(
      members.count(),
      facilities.count(),
      programs.count(),
      reservations.count(),
      notices.count()
    );
  }

  /** 비밀번호 해시를 제외한 회원 목록을 페이지별로 조회한다. */
  @GetMapping("/users")
  public Page<UserSummary> users(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST
    );
    return members
      .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
      .map(UserSummary::of);
  }

  /** 관리자만 회원 권한을 변경하며, 자기 계정의 강등은 막는다. */
  @PatchMapping("/users/{id}/role")
  @Transactional
  public UserSummary changeRole(
    @PathVariable Long id,
    @Valid @RequestBody RoleRequest request,
    @AuthenticationPrincipal Jwt jwt
  ) {
    if (
      id.toString().equals(jwt.getSubject()) &&
      request.role() != Member.Role.ADMIN
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "자기 관리자 권한은 해제할 수 없습니다"
    );
    Member member = members
      .findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (
      "admin".equals(member.username) && request.role() != Member.Role.ADMIN
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "기본 관리자 권한은 해제할 수 없습니다"
    );
    member.role = request.role();
    return UserSummary.of(member);
  }

  /** 관리자가 심사용 계정을 포함한 새 회원을 등록한다. */
  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  public UserSummary createUser(@Valid @RequestBody UserCreateRequest request) {
    return UserSummary.of(
      memberService.createByAdmin(
        request.fullName(),
        request.username(),
        request.password(),
        request.birthDate(),
        request.email(),
        request.phoneNumber(),
        request.gender(),
        request.role()
      )
    );
  }

  /** 관리자가 기본 admin 외의 회원과 활동 데이터를 즉시 삭제한다. */
  @DeleteMapping("/users/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteUser(
    @PathVariable Long id,
    @AuthenticationPrincipal Jwt jwt
  ) {
    if (
      id.toString().equals(jwt.getSubject())
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "현재 로그인한 자기 계정은 삭제할 수 없습니다"
    );
    memberService.hardDeleteById(id);
  }

  /** 관리자가 탈퇴 유예 중인 회원을 복구한다. */
  @PostMapping("/users/{id}/restore")
  public UserSummary restoreUser(@PathVariable Long id) {
    return UserSummary.of(memberService.restoreByAdmin(id));
  }

  /** 관리자가 새 공지사항을 작성한다. */
  @PostMapping("/notices")
  @ResponseStatus(HttpStatus.CREATED)
  public NoticeSummary createNotice(@Valid @RequestBody NoticeRequest request) {
    return NoticeSummary.of(
      notices.save(new Notice(request.title().trim(), request.content().trim()))
    );
  }

  /** 관리자가 공지사항 제목과 본문을 수정한다. */
  @PutMapping("/notices/{id}")
  @Transactional
  public NoticeSummary updateNotice(
    @PathVariable Long id,
    @Valid @RequestBody NoticeRequest request
  ) {
    Notice notice = notices
      .findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    notice.title = request.title().trim();
    notice.content = request.content().trim();
    return NoticeSummary.of(notice);
  }

  /** 관리자가 공지사항을 삭제한다. */
  @DeleteMapping("/notices/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteNotice(@PathVariable Long id) {
    if (!notices.existsById(id)) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND
    );
    notices.deleteById(id);
  }

  public record Overview(
    long users,
    long facilities,
    long programs,
    long reservations,
    long notices
  ) {}

  public record RoleRequest(@NotNull Member.Role role) {}

  public record UserCreateRequest(
    @NotBlank @Size(max = 50) String fullName,
    @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{2,29}") String username,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotNull @Past LocalDate birthDate,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 15) String phoneNumber,
    @NotNull Member.Gender gender,
    @NotNull Member.Role role
  ) {}

  public record NoticeRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 5000) String content
  ) {}

  public record UserSummary(
    Long id,
    String username,
    String fullName,
    String email,
    String phoneNumber,
    Member.Role role,
    Instant createdAt,
    Instant deletionRequestedAt
  ) {
    static UserSummary of(Member member) {
      return new UserSummary(
        member.id,
        member.username,
        member.fullName,
        member.email,
        member.phoneNumber,
        member.getRole(),
        member.createdAt,
        member.deletionRequestedAt
      );
    }
  }

  public record NoticeSummary(
    Long id,
    String title,
    String content,
    Instant createdAt
  ) {
    static NoticeSummary of(Notice notice) {
      return new NoticeSummary(
        notice.id,
        notice.title,
        notice.content,
        notice.createdAt
      );
    }
  }
}
