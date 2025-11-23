package org.example.model;

public class CourtCase {
    public String caseNumber;
    public String url;
    public String category;
    public String plaintiff;
    public String defendant;
    public String judge;
    public String status;

    public CourtCase(String caseNumber, String url,
                     String category, String plaintiff, String defendant,
                     String judge, String status) {
        this.caseNumber = caseNumber != null ? caseNumber : "";
        this.url = url != null ? url : "";
        this.category = category != null ? category : "";
        this.plaintiff = plaintiff != null ? plaintiff : "";
        this.defendant = defendant != null ? defendant : "";
        this.judge = judge != null ? judge : "";
        this.status = status != null ? status : "";
    }

    public boolean matches(String query) {
        if (query == null || query.trim().isEmpty()) return false;
        String q = query.toLowerCase().trim();

        return (plaintiff != null && plaintiff.toLowerCase().contains(q)) ||
                (defendant != null && defendant.toLowerCase().contains(q)) ||
                (caseNumber != null && caseNumber.toLowerCase().contains(q));
    }

    public String getCaseNumber() { return caseNumber; }
    public String getUrl() { return url; }
    public String getCategory() { return category; }
    public String getPlaintiff() { return plaintiff; }
    public String getDefendant() { return defendant; }
    public String getJudge() { return judge; }
    public String getStatus() { return status; }
}