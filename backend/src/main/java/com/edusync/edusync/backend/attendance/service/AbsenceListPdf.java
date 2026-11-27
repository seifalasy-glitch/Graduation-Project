package com.edusync.edusync.backend.attendance.service;

import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;

/**
 * Draws the department's absence list PDF (كشف الطلاب المتجاوزين حد الغياب), copying the
 * faculty's sample: A4 landscape, Arabic right-to-left, two logos top-left, faculty /
 * department / program top-right, centred underlined title, yellow table header, names
 * in dark red, two signature blocks at the bottom.
 *
 * Built as a small XHTML page and turned into PDF by openhtmltopdf (ICU4J does the
 * right-to-left order and the Arabic letter joining). Font: Amiri (SIL OFL, licence in
 * resources/reports/fonts). Logos: resources/reports (taken from the faculty's sample).
 * Every text from the database is HTML-escaped. Nothing is stored; the bytes are returned.
 *
 * Header and signature texts come from settings, with the sample's Arabic texts as
 * defaults (Arabic defaults live here because .properties files are not read as UTF-8).
 * Signature NAMES have no default: set them with env vars, e.g.
 *   APP_REPORTS_SIGNATURE_RIGHT_NAME="أ.د.م. / ..."   APP_REPORTS_SIGNATURE_LEFT_NAME="أ.د. / ..."
 */
@Component
public class AbsenceListPdf {

    /** One table row, already in Arabic. */
    public record Row(String name, String program, String groupAndTime, int absences, String weeks) {
    }

    /** Everything the page shows besides the rows. groupTypeAr = "المعمل" (labs) or "المحاضرة" (lectures). */
    public record Page(String programs, String courseCode, String courseTitle, String termAr, String academicYear,
                       String groupTypeAr, List<Row> rows) {
    }

    private final String faculty;
    private final String department;
    private final String rightTitle;
    private final String rightName;
    private final String leftTitle;
    private final String leftName;

    private final byte[] fontRegular = load("reports/fonts/Amiri-Regular.ttf");
    private final byte[] fontBold = load("reports/fonts/Amiri-Bold.ttf");
    private final String logoUniversity = dataUri("reports/logo-ain-shams.png");
    private final String logoDepartment = dataUri("reports/logo-math-department.png");

    public AbsenceListPdf(
            @Value("${app.reports.faculty:}") String faculty,
            @Value("${app.reports.department:}") String department,
            @Value("${app.reports.signature-right-title:}") String rightTitle,
            @Value("${app.reports.signature-right-name:}") String rightName,
            @Value("${app.reports.signature-left-title:}") String leftTitle,
            @Value("${app.reports.signature-left-name:}") String leftName
    ) {
        this.faculty = orDefault(faculty, "كلية العلوم");
        this.department = orDefault(department, "الرياضيات");
        this.rightTitle = orDefault(rightTitle, "مسؤول تخصص علوم الحاسب");
        this.rightName = rightName.isBlank() ? "...................................." : rightName.trim();
        this.leftTitle = orDefault(leftTitle, "رئيس مجلس قسم الرياضيات");
        this.leftName = leftName.isBlank() ? "...................................." : leftName.trim();
    }

    public byte[] render(Page page) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> new java.io.ByteArrayInputStream(fontRegular), "Amiri", 400,
                    BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> new java.io.ByteArrayInputStream(fontBold), "Amiri", 700,
                    BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
            builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
            builder.defaultTextDirection(BaseRendererBuilder.TextDirection.RTL);
            builder.withHtmlContent(html(page), null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the absence list PDF", e);
        }
    }

    /**
     * The XHTML page (package-private so a test can look at it).
     *
     * Layout note: openhtmltopdf lays out table COLUMNS left-to-right even on an RTL page,
     * so every table is written with dir="ltr" and its cells in visual order from the LEFT
     * (e.g. the list starts with "أسابيع الغياب" and ends with "م" on the right). The text
     * inside each cell is still right-to-left (dir="rtl" on the cells).
     */
    String html(Page p) {
        StringBuilder rows = new StringBuilder();
        int n = 1;
        for (Row r : p.rows()) {
            rows.append("<tr>")
                    .append("<td dir='rtl'>").append(e(r.weeks())).append("</td>")
                    .append("<td dir='rtl' class='count'>").append(r.absences()).append("</td>")
                    .append("<td dir='rtl'>").append(e(r.groupAndTime())).append("</td>")
                    .append("<td dir='rtl'>").append(e(r.program())).append("</td>")
                    .append("<td dir='rtl' class='name'>").append(e(r.name())).append("</td>")
                    .append("<td dir='rtl' class='num'>").append(n++).append("</td>")
                    .append("</tr>");
        }
        if (p.rows().isEmpty()) {
            rows.append("<tr><td dir='rtl' colspan='6' class='empty'>لا يوجد طلاب تجاوزوا حد الغياب</td></tr>");
        }

        return """
                <!DOCTYPE html>
                <html lang="ar">
                <head><meta charset="UTF-8"/>
                <style>
                  @page { size: A4 landscape; margin: 12mm 12mm; }
                  body { font-family: 'Amiri'; font-size: 12pt; color: #000; }
                  table { width: 100%%; border-collapse: collapse; table-layout: fixed; }
                  .top td { vertical-align: top; border: none; padding: 0; }
                  .uni { font-weight: 700; font-size: 13pt; line-height: 1.7; text-align: right; }
                  .title { text-align: center; font-weight: 700; font-size: 15pt; line-height: 1.9; }
                  .title span { border-bottom: 1px solid #555; }
                  .logos { text-align: left; }
                  .logos img { height: 21mm; margin-right: 3mm; }
                  table.list { margin-top: 7mm; }
                  table.list th { background: #ffff00; font-weight: 700; border: 1px solid #000; padding: 4px;
                                  text-align: center; }
                  table.list td { border: 1px solid #000; padding: 7px 4px; text-align: center; }
                  td.name { color: #a00000; font-weight: 700; }
                  td.count { font-weight: 700; }
                  td.empty { padding: 12px; }
                  table.signs { margin-top: 14mm; }
                  .signs td { border: none; font-weight: 700; font-size: 13pt; line-height: 2.2; }
                </style></head>
                <body>
                <table class="top" dir="ltr"><tr>
                  <td class="logos" style="width:24%%"><img src="%s"/><img src="%s"/></td>
                  <td class="title" dir="rtl" style="width:52%%"><span>كشف الطلاب المتجاوزين حد الغياب في — %s</span><br/>
                      <span>%s — %s — للعام الجامعي %s</span></td>
                  <td class="uni" dir="rtl" style="width:24%%">%s<br/>قسم %s<br/>شعبة %s</td>
                </tr></table>
                <table class="list" dir="ltr">
                  <thead><tr>
                    <th dir="rtl" style="width:24%%">أسابيع الغياب</th>
                    <th dir="rtl" style="width:9%%">مرات الغياب</th>
                    <th dir="rtl" style="width:25%%">%s والموعد</th>
                    <th dir="rtl" style="width:11%%">الشعبة</th>
                    <th dir="rtl" style="width:28%%">الاسم</th>
                    <th dir="rtl" style="width:3%%">م</th>
                  </tr></thead>
                  <tbody>%s</tbody>
                </table>
                <table class="signs" dir="ltr"><tr>
                  <td dir="rtl" style="text-align:left; width:50%%">%s<br/>%s</td>
                  <td dir="rtl" style="text-align:right; width:50%%">%s<br/>%s</td>
                </tr></table>
                </body></html>
                """.formatted(
                logoUniversity, logoDepartment,   // sample: Ain Shams on the far left, department seal next to it
                e(p.courseCode()),
                e(p.courseTitle()), e(p.termAr()), e(p.academicYear()),
                e(faculty), e(department), e(p.programs()),
                e(p.groupTypeAr()),
                rows,
                e(leftTitle), e(leftName), e(rightTitle), e(rightName));
    }

    /** Keeps a left-to-right piece (a time range like 10:00–12:00) in order inside Arabic text. */
    public static String ltr(String text) {
        return "⁦" + text + "⁩";   // LEFT-TO-RIGHT ISOLATE ... POP DIRECTIONAL ISOLATE
    }

    private static String e(String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text, "UTF-8");
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static byte[] load(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToByteArray(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing report resource " + path, e);
        }
    }

    private static String dataUri(String path) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(load(path));
    }
}
