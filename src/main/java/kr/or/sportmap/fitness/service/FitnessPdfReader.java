package kr.or.sportmap.fitness.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 애플리케이션 기능을 구성한다. */
@Service
public class FitnessPdfReader {

  private static final int MAX_BYTES = 10 * 1024 * 1024;
  private static final int MAX_PAGES = 5;
  private static final Pattern DATE = Pattern.compile(
    "(?:측정일|검사일|측정일자)\\s*[:：]?\\s*(20\\d{2})[.\\-/년 ]+([01]?\\d)[.\\-/월 ]+([0-3]?\\d)"
  );
  private static final Pattern GRADE = Pattern.compile(
    "(?:체력인증|인증|체력)\\s*등급\\s*[:：]?\\s*([1-6])\\s*등급"
  );
  private static final Pattern PARTICIPATION = Pattern.compile(
    "(?:체력인증|인증결과|인증등급)\\s*[:：]?\\s*참가증"
  );
  private static final Map<String, List<String>> ALIASES = Map.ofEntries(
    Map.entry(
      "shuttle15",
      List.of("15m 왕복오래달리기", "15m 왕복 오래달리기")
    ),
    Map.entry(
      "shuttle20",
      List.of("20m 왕복오래달리기", "20m 왕복 오래달리기")
    ),
    Map.entry("vo2", List.of("최대산소섭취량", "스텝검사", "트레드밀")),
    Map.entry("grip", List.of("상대악력")),
    Map.entry("curlUp", List.of("윗몸말아올리기", "윗몸 말아올리기")),
    Map.entry("repeatJump", List.of("반복점프")),
    Map.entry("sitUp", List.of("교차윗몸일으키기", "교차 윗몸일으키기")),
    Map.entry(
      "flexibility",
      List.of("앉아윗몸앞으로굽히기", "앉아 윗몸 앞으로 굽히기")
    ),
    Map.entry("sideHop", List.of("반복옆뛰기", "반복 옆뛰기")),
    Map.entry("longJump", List.of("제자리멀리뛰기", "제자리 멀리뛰기")),
    Map.entry("handEyeCount", List.of("눈-손 협응력(회)", "눈손협응력(회)")),
    Map.entry("handEyeTime", List.of("눈-손 협응력(초)", "눈손협응력(초)")),
    Map.entry("illinois", List.of("일리노이 민첩성")),
    Map.entry("airTime", List.of("체공시간")),
    Map.entry("shuttle10", List.of("10m 왕복달리기", "10미터 왕복달리기")),
    Map.entry("reaction", List.of("반응시간")),
    Map.entry("walk2min", List.of("2분 제자리걷기", "2분 제자리 걷기")),
    Map.entry("walk6min", List.of("6분 걷기")),
    Map.entry(
      "chairStand",
      List.of("30초 의자앉았다 일어서기", "의자에 앉았다 일어서기")
    ),
    Map.entry("upGo3m", List.of("3m 왕복걷기", "3m 보행")),
    Map.entry("figure8", List.of("8자보행", "8자 보행")),
    Map.entry("bmi", List.of("BMI", "체질량지수")),
    Map.entry("bodyFat", List.of("체지방률")),
    Map.entry("waistHeightRatio", List.of("허리둘레-신장비", "WHtR"))
  );

  private final String tesseractCommand;

  public FitnessPdfReader(
    @Value("${app.fitness.tesseract-command:tesseract}") String tesseractCommand
  ) {
    this.tesseractCommand = tesseractCommand;
  }

  public record Parsed(
    LocalDate measuredOn,
    Integer reportedOfficialGrade,
    Map<String, Double> values,
    boolean usedOcr,
    List<String> warnings
  ) {}

  public Parsed parse(MultipartFile file) throws IOException {
    if (
      file == null || file.isEmpty() || file.getSize() > MAX_BYTES
    ) throw new IllegalArgumentException("10MB 이하의 PDF를 선택해 주세요.");
    byte[] bytes = file.getBytes();
    if (
      bytes.length < 5 ||
      !Arrays.equals(
        Arrays.copyOf(bytes, 5),
        "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII)
      )
    ) throw new IllegalArgumentException(
      "PDF 형식의 파일만 업로드할 수 있습니다."
    );
    StringBuilder text = new StringBuilder();
    boolean usedOcr = false;
    try (PDDocument pdf = Loader.loadPDF(bytes)) {
      if (pdf.isEncrypted()) throw new IllegalArgumentException(
        "암호가 없는 PDF를 업로드해 주세요."
      );
      if (
        pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > MAX_PAGES
      ) throw new IllegalArgumentException(
        "결과지는 1~5쪽 PDF만 처리할 수 있습니다."
      );
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        String pageText = stripper.getText(pdf);
        if (pageText.replaceAll("\\s", "").length() < 20) {
          pageText = ocrPage(pdf, page - 1);
          usedOcr = true;
        }
        text.append(pageText).append('\n');
      }
    }
    String normalized = text
      .toString()
      .replace('\u00a0', ' ')
      .replaceAll("[ \\t]+", " ");
    if (normalized.length() > 100_000) normalized = normalized.substring(
      0,
      100_000
    );
    return parseExtractedText(normalized, usedOcr);
  }

  Parsed parseExtractedText(String normalized, boolean usedOcr) {
    // OCR이 "측 정 일"처럼 한글 음절 사이에 넣은 공백을 정리한다.
    normalized = normalized.replaceAll("(?<=[가-힣])[ \\t]+(?=[가-힣])", "");
    LocalDate measuredOn = null;
    Matcher date = DATE.matcher(normalized);
    if (date.find()) {
      try {
        measuredOn = LocalDate.of(
          Integer.parseInt(date.group(1)),
          Integer.parseInt(date.group(2)),
          Integer.parseInt(date.group(3))
        );
      } catch (RuntimeException ignored) {
        /* reviewer can enter the date */
      }
    }
    Matcher grade = GRADE.matcher(normalized);
    Integer official = grade.find()
      ? Integer.valueOf(grade.group(1))
      : PARTICIPATION.matcher(normalized).find()
        ? 0
        : null;
    Map<String, Double> values = new LinkedHashMap<>();
    for (var entry : ALIASES.entrySet()) {
      for (String alias : entry.getValue()) {
        Pattern p = Pattern.compile(
          Pattern.quote(alias) +
            "\\s*(?:\\([^)]{1,25}\\))?\\s*[:：]?\\s*(-?\\d+(?:\\.\\d+)?)",
          Pattern.CASE_INSENSITIVE
        );
        Matcher match = p.matcher(normalized);
        if (match.find()) {
          values.put(entry.getKey(), Double.valueOf(match.group(1)));
          break;
        }
      }
    }
    List<String> warnings = new ArrayList<>();
    if (measuredOn == null) warnings.add("검사 날짜를 확인해 주세요.");
    if (official == null && values.isEmpty()) warnings.add(
      "인증등급과 검사 수치를 읽지 못했습니다. 결과지 형식을 확인해 주세요."
    );
    warnings.add("PDF 인식값은 저장 전 반드시 원본 결과지와 대조해 주세요.");
    return new Parsed(measuredOn, official, values, usedOcr, warnings);
  }

  private String ocrPage(PDDocument pdf, int page) throws IOException {
    Path image = Files.createTempFile("sportmap-fitness-", ".png");
    Path output = Files.createTempFile("sportmap-fitness-ocr-", ".txt");
    try {
      ImageIO.write(
        new PDFRenderer(pdf).renderImageWithDPI(page, 180),
        "png",
        image.toFile()
      );
      Process process;
      try {
        process = new ProcessBuilder(
          tesseractCommand,
          image.toString(),
          "stdout",
          "-l",
          "kor+eng",
          "--psm",
          "6"
        )
          .redirectErrorStream(true)
          .redirectOutput(output.toFile())
          .start();
      } catch (IOException ex) {
        throw new IllegalArgumentException(
          "이미지 PDF를 읽으려면 서버에 Tesseract OCR과 kor/eng 언어 데이터를 설치해야 합니다."
        );
      }
      try {
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
          process.destroyForcibly();
          throw new IllegalArgumentException(
            "OCR 처리 시간이 초과되었습니다. 더 선명한 결과지를 사용해 주세요."
          );
        }
        String result = Files.readString(output);
        if (
          process.exitValue() != 0 || result.isBlank()
        ) throw new IllegalArgumentException(
          "OCR에서 텍스트를 읽지 못했습니다. 한국어 언어 데이터와 PDF 품질을 확인해 주세요."
        );
        return result;
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IOException("OCR interrupted", ex);
      }
    } finally {
      Files.deleteIfExists(image);
      Files.deleteIfExists(output);
    }
  }
}
