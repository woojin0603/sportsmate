package kr.or.sportmap.facility.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import kr.or.sportmap.facility.domain.Facility;
import kr.or.sportmap.facility.domain.QFacility;
import org.springframework.data.domain.*;

/** 데이터베이스 조회와 저장을 담당한다. */
public class FacilitySearchRepositoryImpl implements FacilitySearchRepository {

  private final JPAQueryFactory query;

  public FacilitySearchRepositoryImpl(EntityManager em) {
    this.query = new JPAQueryFactory(em);
  }

  @Override
  public Page<Facility> search(
    String keyword,
    String regionCode,
    Boolean publicOnly,
    Pageable pageable
  ) {
    QFacility f = QFacility.facility;
    BooleanBuilder where = new BooleanBuilder();
    if (keyword != null && !keyword.isBlank()) where.and(
      f.name.containsIgnoreCase(keyword.trim())
    );
    if (regionCode != null && !regionCode.isBlank()) where.and(
      f.region.code.eq(regionCode)
    );
    if (Boolean.TRUE.equals(publicOnly)) where.and(f.publicFacility.isTrue());
    List<Facility> rows = query
      .selectFrom(f)
      .leftJoin(f.region)
      .fetchJoin()
      .where(where)
      .orderBy(f.id.asc())
      .offset(pageable.getOffset())
      .limit(pageable.getPageSize())
      .fetch();
    Long count = query.select(f.count()).from(f).where(where).fetchOne();
    return new PageImpl<>(rows, pageable, count == null ? 0 : count);
  }
}
