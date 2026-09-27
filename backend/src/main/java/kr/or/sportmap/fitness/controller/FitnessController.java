package kr.or.sportmap.fitness.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.or.sportmap.fitness.domain.FitnessAssessment;
import kr.or.sportmap.fitness.repository.FitnessAssessmentRepository;
import kr.or.sportmap.fitness.service.FitnessPdfReader;
import kr.or.sportmap.fitness.service.FitnessScoringService;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.member.service.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api/fitness")
public class FitnessController {

  private final FitnessPdfReader pdfReader;
  private final FitnessScoringService scoring;
  private final FitnessAssessmentRepository assessments;
  private final MemberService members;
  private final ObjectMapper mapper;

  public FitnessController(
    FitnessPdfReader pdfReader,
    FitnessScoringService scoring,
    FitnessAssessmentRepository assessments,
    MemberService members,
    ObjectMapper mapper
  ) {
    this.pdfReader = pdfReader;
    this.scoring = scoring;
    this.assessments = assessments;
    this.members = members;
    this.mapper = mapper;
  }

  @PostMapping(path = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ParseResponse parse(
    @AuthenticationPrincipal Jwt jwt,
    @RequestPart("file") MultipartFile file
  ) throws IOException {
    Member member = members.findAuthenticated(jwt.getSubject());
    FitnessPdfReader.Parsed parsed = pdfReader.parse(file);
    FitnessScoringService.Result preview = null;
    if (parsed.measuredOn() != null) {
      try {
        preview = scoring.evaluate(
          member,
          parsed.measuredOn(),
          parsed.values(),
          parsed.reportedOfficialGrade()
        );
      } catch (IllegalArgumentException ex) {
        var warnings = new java.util.ArrayList<>(parsed.warnings());
        warnings.add(ex.getMessage());
        parsed = new FitnessPdfReader.Parsed(
          parsed.measuredOn(),
          parsed.reportedOfficialGrade(),
          parsed.values(),
          parsed.usedOcr(),
          warnings
        );
      }
    }
    return new ParseResponse(parsed, preview);
  }

  @PostMapping("/assessments")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public AssessmentResponse save(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody SaveRequest request
  ) throws JsonProcessingException {
    Member member = members.findAuthenticated(jwt.getSubject());
    FitnessScoringService.Result result = scoring.evaluate(
      member,
      request.measuredOn(),
      request.values(),
      request.reportedOfficialGrade()
    );
    if (!"READY".equals(result.status())) throw new IllegalArgumentException(
      "추출값이 불완전하거나 결과지 등급과 다릅니다. 수치를 확인해 주세요: " +
        String.join(", ", result.missing())
    );
    String json = mapper.writeValueAsString(
      request.values() == null ? Map.of() : request.values()
    );
    FitnessAssessment saved = assessments.save(
      new FitnessAssessment(
        member,
        request.measuredOn(),
        result.ageAtTest(),
        result.stage(),
        result.grade(),
        result.score(),
        result.equivalentOfficialGrade(),
        "USER_REVIEWED_PDF",
        json
      )
    );
    return AssessmentResponse.of(saved, mapper);
  }

  @PostMapping("/preview")
  public FitnessScoringService.Result preview(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody SaveRequest request
  ) {
    Member member = members.findAuthenticated(jwt.getSubject());
    return scoring.evaluate(
      member,
      request.measuredOn(),
      request.values(),
      request.reportedOfficialGrade()
    );
  }

  @GetMapping("/assessments")
  @Transactional(readOnly = true)
  public List<AssessmentResponse> list(@AuthenticationPrincipal Jwt jwt) {
    Member member = members.findAuthenticated(jwt.getSubject());
    return assessments
      .findByMemberIdOrderByMeasuredOnDescIdDesc(member.id)
      .stream()
      .map(item -> AssessmentResponse.of(item, mapper))
      .toList();
  }

  public record SaveRequest(
    @NotNull LocalDate measuredOn,
    Map<String, Double> values,
    Integer reportedOfficialGrade
  ) {}

  public record ParseResponse(
    FitnessPdfReader.Parsed parsed,
    FitnessScoringService.Result preview
  ) {}

  public record AssessmentResponse(
    Long id,
    LocalDate measuredOn,
    int ageAtTest,
    String stage,
    String grade,
    int score,
    Integer equivalentOfficialGrade,
    String source,
    Map<String, Double> values,
    Instant createdAt
  ) {
    static AssessmentResponse of(FitnessAssessment item, ObjectMapper mapper) {
      try {
        Map<String, Double> values = mapper.readValue(
          item.metricsJson,
          new TypeReference<Map<String, Double>>() {}
        );
        return new AssessmentResponse(
          item.id,
          item.measuredOn,
          item.ageAtTest,
          item.stage,
          item.grade,
          item.score,
          item.equivalentOfficialGrade,
          item.source,
          values,
          item.createdAt
        );
      } catch (JsonProcessingException ex) {
        throw new IllegalStateException(
          "Stored fitness values are invalid",
          ex
        );
      }
    }
  }
}
