package kr.or.sportmap.region.controller;

import java.util.List;
import kr.or.sportmap.region.repository.RegionRepository;
import org.springframework.web.bind.annotation.*;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api/regions")
public class RegionController {

  private final RegionRepository repository;

  public RegionController(RegionRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  public List<RegionResponse> list() {
    return repository
      .findAll()
      .stream()
      .map(r ->
        new RegionResponse(
          r.id,
          r.code,
          r.name,
          r.parent == null ? null : r.parent.getId()
        )
      )
      .toList();
  }

  public record RegionResponse(
    Long id,
    String code,
    String name,
    Long parentId
  ) {}
}
