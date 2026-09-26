package kr.or.sportmap.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import kr.or.sportmap.facility.domain.Facility;
import kr.or.sportmap.facility.domain.FacilitySource;
import kr.or.sportmap.facility.repository.FacilityRepository;
import kr.or.sportmap.facility.repository.FacilitySourceRepository;
import kr.or.sportmap.program.domain.Program;
import kr.or.sportmap.program.repository.ProgramRepository;
import kr.or.sportmap.region.domain.Region;
import kr.or.sportmap.region.repository.RegionRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionTemplate;

/** 로컬·제출 환경에서 실제 공공 프로그램 미리보기를 중복 없이 적재한다. */
@Configuration
@Profile({ "local", "submission" })
@ConditionalOnProperty(
  prefix = "app.demo",
  name = "enabled",
  havingValue = "true"
)
public class LocalPublicProgramData {

  private static final String DATASET = "KS_PUBLIC_ALSFC_PROGRM_INFO";

  /** 제공받은 2026년 7월 파일에서 추린 미리보기 자료를 한 트랜잭션으로 저장한다. */
  @Bean
  @Order(0)
  ApplicationRunner loadPublicPrograms(
    ObjectMapper mapper,
    TransactionTemplate transactions,
    RegionRepository regions,
    FacilityRepository facilities,
    FacilitySourceRepository sources,
    ProgramRepository programs
  ) {
    return args -> {
      try (
        InputStream stream = getClass().getResourceAsStream(
          "/public-programs-preview.json"
        )
      ) {
        if (stream == null) throw new IllegalStateException(
          "public-programs-preview.json is missing"
        );
        JsonNode rows = mapper.readTree(stream);
        transactions.executeWithoutResult(ignored ->
          saveRows(rows, regions, facilities, sources, programs)
        );
      }
    };
  }

  /** 동일한 원천 시설을 재사용하고 각 프로그램의 기간·가격·연락처를 보존한다. */
  private void saveRows(
    JsonNode rows,
    RegionRepository regions,
    FacilityRepository facilities,
    FacilitySourceRepository sources,
    ProgramRepository programs
  ) {
    Map<String, Region> regionCache = new HashMap<>();
    Map<String, Facility> facilityCache = new HashMap<>();
    for (JsonNode row : rows) {
      // 파일형 H2를 다시 열 때 이미 적재된 원천 프로그램은 중복 생성하지 않는다.
      String programKey = string(row, "sourceKey");
      if (programs.findBySourceKey(programKey).isPresent()) {
        continue;
      }
      String regionCode = string(row, "regionCode");
      Region region =
        regionCode == null
          ? null
          : regionCache.computeIfAbsent(regionCode, code ->
              regions
                .findByCode(code)
                .orElseGet(() ->
                  regions.save(
                    new Region(
                      code,
                      string(row, "regionName") == null
                        ? code
                        : string(row, "regionName"),
                      null
                    )
                  )
                )
            );
      String facilityKey = string(row, "facilitySourceKey");
      Facility facility = facilityCache.computeIfAbsent(facilityKey, key -> {
        FacilitySource existing = sources
          .findByDatasetCodeAndSourceKey(DATASET, key)
          .orElse(null);
        if (existing != null) {
          return existing.facility;
        }
        Facility item = new Facility(string(row, "facilityName"), region);
        item.roadAddress = string(row, "address");
        item.type = string(row, "facilityType");
        item.phone = string(row, "phone");
        item.website = string(row, "registrationUrl");
        item.latitude = decimal(row, "latitude");
        item.longitude = decimal(row, "longitude");
        item.publicFacility = true;
        item.reservable = false;
        facilities.save(item);
        sources.save(new FacilitySource(item, DATASET, key, null));
        return item;
      });
      Program program = Program.newImported();
      program.sourceKey = programKey;
      program.facility = facility;
      program.name = string(row, "name");
      program.sportType = string(row, "sportType");
      program.scheduleText = string(row, "scheduleText");
      program.eligibility = string(row, "eligibility");
      program.fee = decimal(row, "fee");
      program.capacity = row.path("capacity").isNumber()
        ? row.path("capacity").asInt()
        : null;
      program.beginsOn = LocalDate.parse(string(row, "beginsOn"));
      program.endsOn = LocalDate.parse(string(row, "endsOn"));
      program.registrationUrl = string(row, "registrationUrl");
      // 원천 자료는 잔여 좌석과 접수 가능 상태를 보장하지 않는다.
      program.bookingSupported = false;
      programs.save(program);
    }
  }

  /** JSON의 빈 문자열과 null을 모두 선택 입력값으로 처리한다. */
  private static String string(JsonNode row, String field) {
    JsonNode value = row.path(field);
    return value.isMissingNode() || value.isNull() || value.asText().isBlank()
      ? null
      : value.asText();
  }

  /** 숫자 필드를 데이터베이스의 십진수 값으로 변환한다. */
  private static BigDecimal decimal(JsonNode row, String field) {
    String value = string(row, field);
    return value == null ? null : new BigDecimal(value);
  }
}
