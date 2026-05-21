package talan.pfe.rulengine.services.serviceImpl;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.entites.AiInsight;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.RuleSetVersion;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.RuleSetVersionRepository;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentationPdfService {

    private final RuleSetRepository ruleSetRepository;
    private final AiInsightRepository aiInsightRepository;
    private final RuleSetVersionRepository ruleSetVersionRepository;
    private final PlatformGuideAgent platformGuideAgent;

    @Transactional(readOnly = true)
    public byte[] buildRuleSetDocumentationPdf(Long ruleSetId, Long tenantId) {
        RuleSet ruleSet = ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet not found with id: " + ruleSetId));
        AiInsight insight = aiInsightRepository
                .findFirstByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
                        ruleSetId, tenantId, InsightType.DOCUMENTATION_GENERATED)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune documentation IA trouvée."));

        List<RuleSetVersion> versions = ruleSetVersionRepository.findByRuleSetIdOrderByVersionNumberDesc(ruleSetId);
        String html = buildRuleSetPdfHtml(ruleSet, insight, versions);
        return renderHtmlPdfWithFallback(html, "Documentation IA du RuleSet", insight.getDescription());
    }

    public byte[] buildEngineGuidePdf() {
        String content = platformGuideAgent.generateGuide();
        return createPdf("Comment utiliser le moteur de regles", content);
    }

    private byte[] createPdf(String title, String content) {
        String html = buildHtmlTemplate(title, content);
        return renderHtmlPdfWithFallback(html, title, content);
    }

    private byte[] renderHtmlPdfWithFallback(String html, String title, String fallbackContent) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("HTML/CSS PDF rendering failed, switching to fallback renderer: {}", e.getMessage());
            return createFallbackPdf(baos, title, fallbackContent);
        }
    }

    private byte[] createFallbackPdf(ByteArrayOutputStream baos, String title, String content) {
        try {
            Document doc = new Document();
            PdfWriter.getInstance(doc, baos);
            doc.open();
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            doc.add(new Paragraph(title + " (fallback renderer)", titleFont));
            doc.add(new Paragraph("Genere le " + LocalDateTime.now(), bodyFont));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(content, bodyFont));
            doc.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de generer le PDF.", ex);
        }
    }

    private String buildHtmlTemplate(String title, String content) {
        String now = LocalDateTime.now().toString().replace("T", " ");
        String formatted = toStructuredHtml(content);
        String template = """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8" />
                  <style>
                    @page {
                      size: A4;
                      margin: 20mm 14mm 18mm 14mm;
                    }
                    body {
                      font-family: "Segoe UI", Arial, Helvetica, sans-serif;
                      color: #0f172a;
                      font-size: 10.8pt;
                      line-height: 1.65;
                      background: #f8fbff;
                    }
                    .shell {
                      border: 1px solid #dbeafe;
                      border-radius: 14px;
                      background: #ffffff;
                      padding: 0;
                    }
                    .cover {
                      background: linear-gradient(135deg, #0f2f7a 0%, #1d4ed8 65%, #2563eb 100%);
                      padding: 22px;
                      border-radius: 12px 12px 0 0;
                      margin-bottom: 0;
                      color: white;
                    }
                    .title {
                      font-size: 23pt;
                      font-weight: 700;
                      margin: 0 0 8px 0;
                      color: #ffffff;
                    }
                    .subtitle {
                      margin: 0 0 4px 0;
                      font-size: 9.6pt;
                      color: #dbeafe;
                    }
                    .meta-badge {
                      display: inline-block;
                      margin-top: 10px;
                      font-size: 8.5pt;
                      background: rgba(255,255,255,0.2);
                      border: 1px solid rgba(255,255,255,0.35);
                      color: #eff6ff;
                      padding: 4px 9px;
                      border-radius: 999px;
                    }
                    .content {
                      padding: 18px;
                      background: #ffffff;
                    }
                    h2 {
                      margin: 16px 0 10px;
                      font-size: 13pt;
                      color: #0f2f7a;
                      background: #eff6ff;
                      border: 1px solid #bfdbfe;
                      border-radius: 8px;
                      padding: 8px 10px;
                    }
                    p {
                      margin: 0 0 10px 0;
                    }
                    ul {
                      margin: 0 0 14px 16px;
                      padding: 0;
                    }
                    li {
                      margin: 0 0 6px 0;
                    }
                    li::marker {
                      color: #2563eb;
                    }
                    .section-card {
                      border: 1px solid #e2e8f0;
                      border-radius: 10px;
                      background: #ffffff;
                      padding: 12px;
                      margin-bottom: 10px;
                    }
                    .legend-card {
                      border: 1px solid #bfdbfe;
                      border-radius: 10px;
                      background: #eff6ff;
                      padding: 10px 12px;
                      margin: 0 0 12px 0;
                    }
                    .legend-card h3 {
                      margin: 0 0 8px 0;
                      font-size: 10pt;
                      color: #1e3a8a;
                    }
                    .legend-card p {
                      margin: 0 0 6px 0;
                    }
                    .legend-pill {
                      display: inline-block;
                      min-width: 18px;
                      text-align: center;
                      border-radius: 999px;
                      font-weight: 700;
                      margin-right: 6px;
                      padding: 1px 6px;
                      font-size: 9pt;
                      border: 1px solid transparent;
                    }
                    .legend-pill.conv {
                      background: #dbeafe;
                      border-color: #93c5fd;
                      color: #1d4ed8;
                    }
                    .legend-pill.flag {
                      background: #fee2e2;
                      border-color: #fca5a5;
                      color: #991b1b;
                    }
                    .step-note {
                      margin: 0 0 8px 0;
                      border-radius: 8px;
                      padding: 6px 8px;
                      font-size: 9.5pt;
                    }
                    .step-note.conv {
                      background: #eff6ff;
                      border: 1px solid #bfdbfe;
                      color: #1e3a8a;
                    }
                    .step-note.flag {
                      background: #fff1f2;
                      border: 1px solid #fecdd3;
                      color: #9f1239;
                    }
                    .note-symbol {
                      display: inline-block;
                      font-weight: 700;
                      width: 16px;
                    }
                    .footer {
                      margin-top: 14px;
                      font-size: 8.5pt;
                      color: #64748b;
                      text-align: center;
                      border-top: 1px solid #e2e8f0;
                      padding-top: 8px;
                    }
                  </style>
                </head>
                <body>
                  <div class="shell">
                    <section class="cover">
                      <h1 class="title">__TITLE__</h1>
                      <p class="subtitle">Document genere automatiquement par la plateforme RaaS</p>
                      <p class="subtitle">Date de generation: __NOW__</p>
                      <div class="meta-badge">RaaS Premium PDF v3</div>
                    </section>
                    <section class="content">
                      __CONTENT__
                    </section>
                    <div class="footer">RaaS - Rule Engine Documentation</div>
                  </div>
                </body>
                </html>
                """;

        return template
                .replace("__TITLE__", escapeHtml(title))
                .replace("__NOW__", escapeHtml(now))
                .replace("__CONTENT__", formatted);
    }

    private String escapeHtml(String raw) {
        if (raw == null) return "";
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String toStructuredHtml(String content) {
        StringBuilder html = new StringBuilder();
        String[] lines = content == null ? new String[0] : content.split("\\r?\\n");
        boolean inList = false;
        boolean inSectionCard = false;
        boolean legendOpened = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                if (inSectionCard) {
                    html.append("</div>");
                    inSectionCard = false;
                }
                continue;
            }

            if (line.equalsIgnoreCase("Conversion = d") || line.equalsIgnoreCase("Flags = #")) {
                if (!legendOpened) {
                    if (inList) {
                        html.append("</ul>");
                        inList = false;
                    }
                    if (inSectionCard) {
                        html.append("</div>");
                        inSectionCard = false;
                    }
                    html.append("<div class=\"legend-card\">");
                    html.append("<h3>Legende des symboles</h3>");
                    legendOpened = true;
                }
                if (line.equalsIgnoreCase("Conversion = d")) {
                    html.append("<p><span class=\"legend-pill conv\">d</span> Conversion</p>");
                } else {
                    html.append("<p><span class=\"legend-pill flag\">#</span> Flags</p>");
                }
                continue;
            }

            if (line.matches("^\\d+\\).*")) {
                if (legendOpened) {
                    html.append("</div>");
                    legendOpened = false;
                }
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                if (inSectionCard) {
                    html.append("</div>");
                }
                html.append("<h2>").append(escapeHtml(line)).append("</h2>");
                html.append("<div class=\"section-card\">");
                inSectionCard = true;
                continue;
            }

            if (line.startsWith("# Conversion d:")) {
                String text = line.substring("# Conversion d:".length()).trim();
                html.append("<p class=\"step-note conv\"><span class=\"note-symbol\">d</span>")
                        .append(escapeHtml(text))
                        .append("</p>");
                continue;
            }

            if (line.startsWith("# Flags #:")) {
                String text = line.substring("# Flags #:".length()).trim();
                html.append("<p class=\"step-note flag\"><span class=\"note-symbol\">#</span>")
                        .append(escapeHtml(text))
                        .append("</p>");
                continue;
            }

            if (line.startsWith("- ")) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(escapeHtml(line.substring(2).trim())).append("</li>");
                continue;
            }

            if (inList) {
                html.append("</ul>");
                inList = false;
            }
            html.append("<p>").append(escapeHtml(line)).append("</p>");
        }

        if (inList) {
            html.append("</ul>");
        }
        if (legendOpened) {
            html.append("</div>");
        }
        if (inSectionCard) {
            html.append("</div>");
        }
        return html.toString();
    }

    private String buildRuleSetPdfHtml(RuleSet ruleSet, AiInsight insight, List<RuleSetVersion> versions) {
        StringBuilder timeline = new StringBuilder();
        for (RuleSetVersion v : versions) {
            timeline.append("""
                    <div class="timeline-item">
                      <div class="dot"></div>
                      <div class="timeline-content">
                        <h4>Version %s</h4>
                        <p>Par %s le %s</p>
                        <small>%s</small>
                      </div>
                    </div>
                    """.formatted(
                    escapeHtml(String.valueOf(v.getVersionNumber())),
                    escapeHtml(v.getCreatedBy() != null ? v.getCreatedBy().getEmail() : "système"),
                    escapeHtml(formatDate(v.getCreatedAt())),
                    escapeHtml(v.getChangeNote() == null ? "Aucune note" : v.getChangeNote())
            ));
        }

        String doc = escapeHtml(insight.getDescription()).replace("\n", "<br/>");
        String statusClass = insight.getStatus().name().equals("ACCEPTED") ? "ok" :
                (insight.getStatus().name().equals("REJECTED") ? "off" : "pending");

        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8" />
                  <style>
                    @page { size: A4; margin: 14mm; }
                    body { font-family: "Segoe UI", Arial, sans-serif; background: #f8fbff; color: #0f172a; font-size: 10.3pt; line-height: 1.5; }
                    .sheet { border: 1px solid #dbeafe; border-radius: 12px; overflow: hidden; background: #fff; }
                    .cover { padding: 18px; background: linear-gradient(135deg,#0f2f7a,#2563eb); color: #fff; }
                    .cover h1 { margin: 0 0 6px; font-size: 20pt; }
                    .cover p { margin: 0; color: #dbeafe; font-size: 9pt; }
                    .metrics { display: grid; grid-template-columns: repeat(4,1fr); gap: 8px; padding: 14px; }
                    .metric { border: 1px solid #dbeafe; border-radius: 8px; padding: 8px; background: #f8fbff; }
                    .metric b { display:block; font-size: 8pt; color: #64748b; }
                    .metric span { font-size: 11pt; font-weight: 700; color: #0f172a; }
                    .section { margin: 0 14px 12px; border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px; }
                    .section h2 { margin: 0 0 8px; color: #1d4ed8; font-size: 12pt; }
                    .badge { display:inline-block; padding: 3px 8px; border-radius: 999px; font-size: 8pt; font-weight: 700; }
                    .badge.pending { background:#dbeafe; color:#1d4ed8; }
                    .badge.ok { background:#dcfce7; color:#166534; }
                    .badge.off { background:#fee2e2; color:#991b1b; }
                    .timeline-item { position: relative; padding-left: 18px; margin-bottom: 8px; }
                    .dot { position:absolute; left:0; top:5px; width:8px; height:8px; border-radius:999px; background:#3b82f6; }
                    .timeline-content { border:1px solid #dbeafe; background:#f8fbff; border-radius:8px; padding:8px; }
                    .timeline-content h4 { margin:0; font-size:9.5pt; }
                    .timeline-content p { margin:2px 0 4px; font-size:8pt; color:#64748b; }
                    .timeline-content small { color:#334155; font-size:8.3pt; }
                    .footer { text-align:center; font-size:8pt; color:#64748b; padding:8px 0 12px; }
                  </style>
                </head>
                <body>
                  <div class="sheet">
                    <section class="cover">
                      <h1>Documentation IA - %s (v%s)</h1>
                      <p>RaaS Premium PDF v4 • Généré le %s</p>
                    </section>
                    <section class="metrics">
                      <div class="metric"><b>Statut RuleSet</b><span>%s</span></div>
                      <div class="metric"><b>Stratégie</b><span>%s</span></div>
                      <div class="metric"><b>Règles</b><span>%s</span></div>
                      <div class="metric"><b>Statut Doc</b><span>%s</span></div>
                    </section>
                    <section class="section">
                      <h2>Documentation IA</h2>
                      <span class="badge %s">%s</span>
                      <p style="margin-top:8px">%s</p>
                    </section>
                    <section class="section">
                      <h2>Timeline versions</h2>
                      %s
                    </section>
                    <div class="footer">RaaS - Rule Engine Documentation</div>
                  </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(ruleSet.getName()),
                escapeHtml(String.valueOf(ruleSet.getCurrentVersion())),
                escapeHtml(formatDate(LocalDateTime.now())),
                escapeHtml(ruleSet.getStatus().name()),
                escapeHtml(ruleSet.getEvaluationStrategy().name()),
                escapeHtml(String.valueOf(ruleSet.getRules().size())),
                escapeHtml(insight.getStatus().name()),
                statusClass,
                escapeHtml(insight.getStatus().name()),
                doc,
                timeline.toString()
        );
    }

    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) return "-";
        return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }
}
