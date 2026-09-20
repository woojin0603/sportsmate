package kr.or.sportmap.demo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import kr.or.sportmap.community.domain.Comment;
import kr.or.sportmap.community.domain.Notice;
import kr.or.sportmap.community.domain.Question;
import kr.or.sportmap.community.repository.CommentRepository;
import kr.or.sportmap.community.repository.NoticeRepository;
import kr.or.sportmap.community.repository.QuestionRepository;
import kr.or.sportmap.facility.domain.Facility;
import kr.or.sportmap.facility.repository.FacilityRepository;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.member.repository.MemberRepository;
import kr.or.sportmap.program.domain.Program;
import kr.or.sportmap.program.repository.ProgramRepository;
import kr.or.sportmap.recommendation.domain.ExerciseRecommendation;
import kr.or.sportmap.recommendation.repository.ExerciseRecommendationRepository;
import kr.or.sportmap.region.domain.Region;
import kr.or.sportmap.region.repository.RegionRepository;
import kr.or.sportmap.reservation.domain.Reservation;
import kr.or.sportmap.reservation.repository.ReservationRepository;
import kr.or.sportmap.review.domain.FacilityReview;
import kr.or.sportmap.review.repository.FacilityReviewRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Disposable examples for the in-memory H2 profile; never loaded by mysql. */
@Configuration
@Profile("local")
@ConditionalOnProperty(
  prefix = "app.demo",
  name = "enabled",
  havingValue = "true"
)
public class LocalDemoData {

  @Bean
  ApplicationRunner loadDemoData(
    TransactionTemplate transactions,
    RegionRepository regions,
    FacilityRepository facilities,
    ProgramRepository programs,
    MemberRepository members,
    ReservationRepository reservations,
    NoticeRepository notices,
    QuestionRepository questions,
    CommentRepository comments,
    FacilityReviewRepository reviews,
    ExerciseRecommendationRepository recommendations,
    PasswordEncoder passwords
  ) {
    return args ->
      transactions.executeWithoutResult(ignored -> {
        if (members.existsByUsername("demo_user")) return;

        // 공공 프로그램 미리보기에서 이미 생성한 지역 코드는 재사용한다.
        Region seoul = regions
          .findByCode("1100000000")
          .orElseGet(() ->
            regions.save(new Region("1100000000", "서울특별시", null))
          );
        Region seongdong = regions
          .findByCode("1120000000")
          .orElseGet(() ->
            regions.save(new Region("1120000000", "성동구", seoul))
          );
        Facility swim = facilities.save(
          facility(
            "데모 성동 수영장",
            seongdong,
            "서울특별시 성동구 데모로 10",
            "수영장",
            "37.5518979",
            "127.0207529",
            true,
            false
          )
        );
        Facility gym = facilities.save(
          facility(
            "데모 생활체육관",
            seongdong,
            "서울특별시 성동구 데모로 20",
            "체육관",
            "37.5531000",
            "127.0235000",
            true,
            true
          )
        );
        facilities.save(
          facility(
            "데모 야외 운동장",
            seoul,
            "서울특별시 데모로 30",
            "운동장",
            "37.5601000",
            "127.0302000",
            true,
            false
          )
        );

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Program publicProgram = Program.newImported();
        publicProgram.facility = swim;
        publicProgram.name = "데모 공공 수영 강좌";
        publicProgram.sportType = "수영";
        publicProgram.scheduleText = "월수금 / 19:00~19:50";
        publicProgram.eligibility = "성인";
        publicProgram.fee = new BigDecimal("45000.00");
        publicProgram.capacity = 20;
        publicProgram.bookingSupported = false;
        publicProgram.sourceKey = "demo-public-swim";
        publicProgram.beginsOn = today.plusDays(1);
        publicProgram.endsOn = today.plusDays(30);
        publicProgram.registrationUrl = null;
        programs.save(publicProgram);

        Program localProgram = Program.newLocal();
        localProgram.facility = gym;
        localProgram.name = "데모 자체 예약 배드민턴";
        localProgram.sportType = "배드민턴";
        localProgram.scheduleText = "예약 시각 선택";
        localProgram.eligibility = "누구나";
        localProgram.fee = BigDecimal.ZERO;
        localProgram.capacity = 1;
        localProgram.bookingSupported = true;
        programs.save(localProgram);

        Member demo = members.save(
          new Member(
            "테스트 회원",
            "demo_user",
            passwords.encode("Demo1234!"),
            LocalDate.of(1995, 5, 10),
            "demo@sportmap.local",
            "01012345678",
            Member.Gender.OTHER
          )
        );
        Instant begin = publicProgram.beginsOn
          .atStartOfDay(ZoneId.of("Asia/Seoul"))
          .toInstant();
        Instant end = publicProgram.endsOn
          .plusDays(1)
          .atStartOfDay(ZoneId.of("Asia/Seoul"))
          .toInstant();
        reservations.save(
          new Reservation(
            demo,
            publicProgram,
            begin,
            end,
            Reservation.Status.REQUESTED
          )
        );

        notices.save(
          new Notice(
            "데모 공지사항",
            "로컬 H2 화면 확인을 위한 임시 공지입니다."
          )
        );
        Question question = questions.save(
          new Question(
            demo,
            "데모 시설 이용 문의",
            "수영장 운영 시간을 알고 싶습니다."
          )
        );
        comments.save(
          new Comment(
            question,
            demo,
            null,
            "운영기관 홈페이지에서 확인해 주세요."
          )
        );
        reviews.save(
          new FacilityReview(demo, swim, 5, "로컬 데모 리뷰입니다.")
        );
        recommendations.save(
          new ExerciseRecommendation(
            20,
            39,
            "걷기",
            "20~39세 데모 추천 운동입니다."
          )
        );
        recommendations.save(
          new ExerciseRecommendation(
            40,
            69,
            "수영",
            "40~69세 데모 추천 운동입니다."
          )
        );
      });
  }

  private static Facility facility(
    String name,
    Region region,
    String address,
    String type,
    String latitude,
    String longitude,
    boolean publicFacility,
    boolean reservable
  ) {
    Facility result = new Facility(name, region);
    result.roadAddress = address;
    result.type = type;
    result.latitude = new BigDecimal(latitude);
    result.longitude = new BigDecimal(longitude);
    result.publicFacility = publicFacility;
    result.reservable = reservable;
    return result;
  }
}
