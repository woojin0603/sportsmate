package kr.or.sportmap.review.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import kr.or.sportmap.facility.repository.FacilityRepository;
import kr.or.sportmap.member.service.MemberService;
import kr.or.sportmap.review.domain.FacilityReview;
import kr.or.sportmap.review.repository.FacilityReviewRepository;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api/facilities/{facilityId}/reviews")
public class FacilityReviewController {

  private final FacilityReviewRepository repository;
  private final FacilityRepository facilities;
  private final MemberService members;

  public FacilityReviewController(
    FacilityReviewRepository repository,
    FacilityRepository facilities,
    MemberService members
  ) {
    this.repository = repository;
    this.facilities = facilities;
    this.members = members;
  }

  @GetMapping
  public Page<ReviewResponse> list(
    @PathVariable Long facilityId,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST
    );
    return repository
      .findByFacilityId(
        facilityId,
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
      )
      .map(r -> new ReviewResponse(r.id, r.rating, r.content, r.createdAt));
  }

  public record ReviewResponse(
    Long id,
    Integer rating,
    String content,
    Instant createdAt
  ) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ReviewResponse create(
    @PathVariable Long facilityId,
    @Valid @RequestBody ReviewRequest request,
    @AuthenticationPrincipal Jwt jwt
  ) {
    var facility = facilities
      .findById(facilityId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    FacilityReview review = repository.save(
      new FacilityReview(
        members.findAuthenticated(jwt.getSubject()),
        facility,
        request.rating(),
        request.content().trim()
      )
    );
    return new ReviewResponse(
      review.id,
      review.rating,
      review.content,
      review.createdAt
    );
  }

  public record ReviewRequest(
    @NotNull @Min(1) @Max(5) Integer rating,
    @NotBlank @Size(max = 2000) String content
  ) {}
}
