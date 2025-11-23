package org.example;

import org.example.service.CourtSearchService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SwingCourtSearchApp extends JFrame {
    private JTextField searchField;
    private JTextField fromDateField;
    private JTextField toDateField;
    private JButton searchButton;
    private JButton clearButton;
    private JButton todayButton;
    private JButton last30DaysButton;
    private JTable resultsTable;
    private JTextArea detailsArea;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel resultsCountLabel;

    private CourtSearchService searchService;
    private DefaultTableModel tableModel;
    private Thread searchThread;

    public SwingCourtSearchApp() {
        searchService = new CourtSearchService();
        initializeUI();
        setDefaultDates();
    }

    private void initializeUI() {
        setTitle("🔍 LexData");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Основная панель
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Панель поиска
        JPanel searchPanel = createSearchPanel();
        mainPanel.add(searchPanel, BorderLayout.NORTH);

        // Таблица результатов
        JPanel resultsPanel = createResultsPanel();
        mainPanel.add(resultsPanel, BorderLayout.CENTER);

        // Панель деталей
        JPanel detailsPanel = createDetailsPanel();
        mainPanel.add(detailsPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Параметры поиска"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Поле поиска
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel("Запрос:"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1;
        searchField = new JTextField();
        searchField.setToolTipText("ФИО или название организации");
        panel.add(searchField, gbc);

        // Период поиска
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0;
        panel.add(new JLabel("Период:"), gbc);

        gbc.gridx = 1; gbc.weightx = 0.3;
        fromDateField = new JTextField(10);
        fromDateField.setToolTipText("С даты (дд.мм.гггг)");
        panel.add(fromDateField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        panel.add(new JLabel("по"), gbc);

        gbc.gridx = 3; gbc.weightx = 0.3;
        toDateField = new JTextField(10);
        toDateField.setToolTipText("По дату (дд.мм.гггг)");
        panel.add(toDateField, gbc);

        // Кнопки быстрого выбора периода
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 4; gbc.weightx = 1;
        JPanel periodButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        todayButton = new JButton("Сегодня");
        last30DaysButton = new JButton("Последние 30 дней");
        periodButtonsPanel.add(todayButton);
        periodButtonsPanel.add(last30DaysButton);
        panel.add(periodButtonsPanel, gbc);

        // Кнопки управления
        gbc.gridy = 3;
        JPanel controlButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchButton = new JButton("🔍 Начать поиск");
        searchButton.setBackground(new Color(52, 152, 219));
        searchButton.setForeground(Color.WHITE);
        searchButton.setFont(searchButton.getFont().deriveFont(Font.BOLD));

        clearButton = new JButton("🗑️ Очистить");
        controlButtonsPanel.add(searchButton);
        controlButtonsPanel.add(clearButton);
        panel.add(controlButtonsPanel, gbc);

        // Прогресс бар и статус
        gbc.gridy = 4;
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        panel.add(progressBar, gbc);

        gbc.gridy = 5;
        statusLabel = new JLabel("Готов к поиску");
        panel.add(statusLabel, gbc);

        // Обработчики событий
        setupSearchEventHandlers();

        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Результаты поиска"));

        // Таблица
        String[] columnNames = {"№ дела", "Истец", "Ответчик", "Судья", "Статус", "Ссылка"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        resultsTable = new JTable(tableModel);

        // Скрываем колонку со ссылкой
        resultsTable.removeColumn(resultsTable.getColumnModel().getColumn(5));

        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setAutoCreateRowSorter(true);
        resultsTable.getSelectionModel().addListSelectionListener(e -> showSelectedCaseDetails());

        JScrollPane tableScroll = new JScrollPane(resultsTable);
        tableScroll.setPreferredSize(new Dimension(800, 300));

        // Счетчик результатов
        resultsCountLabel = new JLabel("Найдено: 0 дел");
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(resultsCountLabel);

        panel.add(tableScroll, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Детали дела"));

        detailsArea = new JTextArea(6, 20);
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane detailsScroll = new JScrollPane(detailsArea);

        // Кнопки для работы с делом
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton copyButton = new JButton("📋 Копировать ссылку");
        JButton openButton = new JButton("🌐 Открыть в браузере");
        buttonsPanel.add(copyButton);
        buttonsPanel.add(openButton);

        // Обработчики кнопок
        copyButton.addActionListener(e -> copyCaseUrl());
        openButton.addActionListener(e -> openInBrowser());

        panel.add(detailsScroll, BorderLayout.CENTER);
        panel.add(buttonsPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void setupSearchEventHandlers() {
        searchButton.addActionListener(e -> startSearch());
        clearButton.addActionListener(e -> clearResults());
        todayButton.addActionListener(e -> setTodayPeriod());
        last30DaysButton.addActionListener(e -> setLast30Days());
    }

    private void setDefaultDates() {
        setLast30Days();
    }

    private void startSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите запрос для поиска",
                    "Ошибка", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fromDate = fromDateField.getText().trim();
        String toDate = toDateField.getText().trim();

        if (fromDate.isEmpty() || toDate.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Заполните обе даты",
                    "Ошибка", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Останавливаем предыдущий поиск если он идет
        if (searchThread != null && searchThread.isAlive()) {
            searchThread.interrupt();
        }

        // Очищаем предыдущие результаты
        tableModel.setRowCount(0);
        detailsArea.setText("");

        // Запускаем поиск в отдельном потоке
        searchThread = new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(true);
                    searchButton.setEnabled(false);
                    statusLabel.setText("Подготовка к поиску...");
                });

                searchService.searchCasesSwing(
                        query,
                        fromDate,
                        toDate,
                        tableModel,
                        this::updateStatus
                );

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Ошибка при поиске: " + e.getMessage());
                    JOptionPane.showMessageDialog(SwingCourtSearchApp.this,
                            "Ошибка при выполнении поиска: " + e.getMessage(),
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    searchButton.setEnabled(true);
                    updateResultsCount();
                });
            }
        });

        searchThread.setDaemon(true);
        searchThread.start();
    }

    private void clearResults() {
        if (searchThread != null && searchThread.isAlive()) {
            searchThread.interrupt();
        }

        tableModel.setRowCount(0);
        detailsArea.setText("");
        updateResultsCount();
        statusLabel.setText("Результаты очищены");
    }

    private void setLast30Days() {
        toDateField.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        fromDateField.setText(LocalDate.now().minusDays(30).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
    }

    private void setTodayPeriod() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        fromDateField.setText(today);
        toDateField.setText(today);
    }

    private void showSelectedCaseDetails() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow == -1) {
            detailsArea.setText("");
            return;
        }

        // Конвертируем индекс view в model
        int modelRow = resultsTable.convertRowIndexToModel(selectedRow);

        String caseNumber = (String) tableModel.getValueAt(modelRow, 0);
        String plaintiff = (String) tableModel.getValueAt(modelRow, 1);
        String defendant = (String) tableModel.getValueAt(modelRow, 2);
        String judge = (String) tableModel.getValueAt(modelRow, 3);
        String status = (String) tableModel.getValueAt(modelRow, 4);
        String url = (String) tableModel.getValueAt(modelRow, 5);

        String details = String.format(
                "Дело: %s\nИстец: %s\nОтветчик: %s\nСудья: %s\nСтатус: %s\nСсылка: https://kirovskiy--dag.sudrf.ru%s",
                caseNumber, plaintiff, defendant, judge, status, url
        );

        detailsArea.setText(details);
    }

    private void copyCaseUrl() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Выберите дело из таблицы",
                    "Ошибка", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = resultsTable.convertRowIndexToModel(selectedRow);
        String url = (String) tableModel.getValueAt(modelRow, 5);

        if (url != null && !url.isEmpty()) {
            String fullUrl = "https://kirovskiy--dag.sudrf.ru" + url;
            StringSelection stringSelection = new StringSelection(fullUrl);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);

            statusLabel.setText("Ссылка скопирована в буфер обмена");
            JOptionPane.showMessageDialog(this, "Ссылка скопирована!",
                    "Успех", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void openInBrowser() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Выберите дело из таблицы",
                    "Ошибка", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = resultsTable.convertRowIndexToModel(selectedRow);
        String url = (String) tableModel.getValueAt(modelRow, 5);

        if (url != null && !url.isEmpty()) {
            try {
                String fullUrl = "https://kirovskiy--dag.sudrf.ru" + url;
                Desktop.getDesktop().browse(new URI(fullUrl));
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Не удалось открыть ссылку в браузере: " + e.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    private void updateResultsCount() {
        SwingUtilities.invokeLater(() ->
                resultsCountLabel.setText("Найдено: " + tableModel.getRowCount() + " дел")
        );
    }

    public static void main(String[] args) {
        // Устанавливаем системный look and feel (совместимая версия)
        try {
            // Получаем системный Look and Feel
            String systemLookAndFeel = UIManager.getSystemLookAndFeelClassName();
            UIManager.setLookAndFeel(systemLookAndFeel);
        } catch (Exception e) {
            System.err.println("Не удалось установить системный Look and Feel: " + e.getMessage());
            // Продолжаем с дефолтным Look and Feel
        }

        SwingUtilities.invokeLater(() -> {
            new SwingCourtSearchApp().setVisible(true);
        });
    }
}