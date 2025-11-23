package org.example.service;

import org.example.model.CourtCase;
import org.example.model.CourtPageParser;
import org.jsoup.Jsoup;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CourtSearchService {

    private final CourtPageParser parser;
    private final List<String> courts;

    public CourtSearchService() {
        this.parser = new CourtPageParser();
        this.courts = List.of(
                "https://kirovskiy--dag.sudrf.ru/modules.php?name=sud_delo&srv_num=1"
        );
    }

    public void searchCasesSwing(String query, String fromDate, String toDate,
                                 DefaultTableModel tableModel, Consumer<String> statusCallback) {

        try {
            LocalDate from = LocalDate.parse(fromDate, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            LocalDate to = LocalDate.parse(toDate, DateTimeFormatter.ofPattern("dd.MM.yyyy"));

            List<String> dates = generateDates(from, to);
            int totalDates = dates.size();
            int processed = 0;

            for (String court : courts) {
                for (String date : dates) {
                    // Проверяем не прерван ли поток
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    processed++;
                    final int currentProcessed = processed;
                    final String currentDate = date;

                    SwingUtilities.invokeLater(() ->
                            statusCallback.accept(String.format("Обрабатывается дата %s (%d/%d)",
                                    currentDate, currentProcessed, totalDates))
                    );

                    String url = court + "&H_date=" + date;

                    try {
                        String html = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                .timeout(30000)
                                .get()
                                .html();

                        List<CourtCase> cases = parser.parse(html);

                        for (CourtCase courtCase : cases) {
                            if (courtCase.matches(query)) {
                                // Добавляем в таблицу в EDT
                                SwingUtilities.invokeLater(() ->
                                        tableModel.addRow(new Object[]{
                                                courtCase.getCaseNumber(),
                                                courtCase.getPlaintiff(),
                                                courtCase.getDefendant(),
                                                courtCase.getJudge(),
                                                courtCase.getStatus(),
                                                courtCase.getUrl() // Скрытая колонка для ссылки
                                        })
                                );
                            }
                        }

                        Thread.sleep(500);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() ->
                                statusCallback.accept("Ошибка при обработке даты " + date + ": " + e.getMessage())
                        );
                    }
                }
            }

            SwingUtilities.invokeLater(() ->
                    statusCallback.accept("Поиск завершен. Найдено: " + tableModel.getRowCount() + " дел")
            );

        } catch (Exception e) {
            SwingUtilities.invokeLater(() ->
                    statusCallback.accept("Ошибка формата даты: " + e.getMessage())
            );
        }
    }

    private List<String> generateDates(LocalDate from, LocalDate to) {
        List<String> dates = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        LocalDate current = from;
        while (!current.isAfter(to)) {
            dates.add(current.format(fmt));
            current = current.plusDays(1);
        }

        return dates;
    }
}