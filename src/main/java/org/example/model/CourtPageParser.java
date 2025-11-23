package org.example.model;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class CourtPageParser {

    public List<CourtCase> parse(String html) {
        List<CourtCase> cases = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(html);
            Elements rows = doc.select("tr[valign=top]");

            for (Element row : rows) {
                try {
                    CourtCase cc = parseCase(row);
                    if (cc != null) {
                        cases.add(cc);
                    }
                } catch (Exception e) {
                    System.err.println("Ошибка парсинга строки: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка парсинга HTML: " + e.getMessage());
        }

        return cases;
    }

    private CourtCase parseCase(Element row) {
        Elements tds = row.getElementsByTag("td");

        if (tds.size() < 7) {
            return null;
        }

        String caseNumber = safeGetText(tds.get(1));
        String caseUrl = tds.get(1).select("a").attr("href");

        String fullText = tds.get(4).html();

        String category = extract(fullText, "КАТЕГОРИЯ:", "ИСТЕЦ");
        String plaintiff = extract(fullText, "ИСТЕЦ", "ОТВЕТЧИК");
        String defendant = extract(fullText, "ОТВЕТЧИК", "<");

        String judge = safeGetText(tds.get(5));
        String status = safeGetText(tds.get(6));

        if (caseNumber == null || caseNumber.trim().isEmpty()) {
            return null;
        }

        return new CourtCase(
                caseNumber,
                caseUrl,
                category,
                plaintiff,
                defendant,
                judge,
                status
        );
    }

    private String extract(String text, String start, String end) {
        if (text == null) return "";

        int s = text.indexOf(start);
        if (s == -1) return "";
        s += start.length();

        int e = text.indexOf(end, s);
        if (e == -1) return text.substring(s);

        return text.substring(s, e)
                .replace("<br>", "")
                .replace("&nbsp;", " ")
                .replace(":", "")
                .trim();
    }

    private String safeGetText(Element element) {
        return element != null ? element.text().trim() : "";
    }
}