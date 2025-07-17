package Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

public class LanguageTranslatorApp extends JFrame {

    private JComboBox<String> sourceLangBox, targetLangBox;
    private JTextArea inputTextArea, outputTextArea;
    private JButton translateButton, historyButton;

    private final Map<String, String> languageMap = new LinkedHashMap<>();
    private Connection conn;

    private static final String GEMINI_API_KEY = "AIzaSyDjrOXWbQ2a3dirfE2o-i9HljUWveX1XLE";

    public LanguageTranslatorApp() {
        setTitle("Language Translator");
        setSize(600, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        languageMap.put("Arabic", "ar");
        languageMap.put("Chinese ", "zh");
        languageMap.put("Dutch", "nl");
        languageMap.put("English", "en");
        languageMap.put("French", "fr");
        languageMap.put("German", "de");
        languageMap.put("Italian", "it");
        languageMap.put("Japanese", "ja");
        languageMap.put("Korean", "ko");
        languageMap.put("Portuguese", "pt");
        languageMap.put("Russian", "ru");
        languageMap.put("Spanish", "es");
        languageMap.put("Turkish", "tr");
        languageMap.put("Ukrainian", "uk");

        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("From:"));
        sourceLangBox = new JComboBox<>(languageMap.keySet().toArray(new String[0]));
        topPanel.add(sourceLangBox);
        topPanel.add(new JLabel("To:"));
        targetLangBox = new JComboBox<>(languageMap.keySet().toArray(new String[0]));
        topPanel.add(targetLangBox);
        add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(2, 1));
        inputTextArea = new JTextArea("Enter text here...");
        inputTextArea.setLineWrap(true);
        inputTextArea.setWrapStyleWord(true);
        centerPanel.add(new JScrollPane(inputTextArea));

        outputTextArea = new JTextArea("Translation will appear here...");
        outputTextArea.setEditable(false);
        outputTextArea.setLineWrap(true);
        outputTextArea.setWrapStyleWord(true);
        centerPanel.add(new JScrollPane(outputTextArea));
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        translateButton = new JButton("Translate");
        historyButton = new JButton("View History");
        bottomPanel.add(translateButton);
        bottomPanel.add(historyButton);
        add(bottomPanel, BorderLayout.SOUTH);

        setupDatabase();
        translateButton.addActionListener(e -> translateText());
        historyButton.addActionListener(e -> showHistory());

        setVisible(true);
    }

    private void setupDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/translator_app", "root", "Shreyans144"
            );
            Statement stmt = conn.createStatement();

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS translations (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "source_lang VARCHAR(50), " +
                "target_lang VARCHAR(50), " +
                "input_text TEXT, " +
                "output_text TEXT)"
            );

            DatabaseMetaData meta = conn.getMetaData();
            ResultSet columns = meta.getColumns(null, null, "translations", "output_text");
            if (!columns.next()) {
               
                try {
                    stmt.executeUpdate("ALTER TABLE translations ADD COLUMN output_text TEXT");
                    System.out.println("Added 'output_text' column to 'translations' table.");
                } catch (SQLException alterEx) {
                    
                    System.err.println("Failed to add 'output_text' column (it might already exist or another error occurred): " + alterEx.getMessage());
                }
            }
            columns.close();
            stmt.close();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Failed to connect to database or create/alter table. Please ensure MySQL is running " +
                "and 'translator_app' database exists. Error: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void translateText() {
        String sourceLang = languageMap.get(sourceLangBox.getSelectedItem().toString());
        String targetLang = languageMap.get(targetLangBox.getSelectedItem().toString());
        String input = inputTextArea.getText().trim();

        if (input.isEmpty()) {
            outputTextArea.setText("❗ Please enter text to translate.");
            return;
        }

        if (GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY_HERE") || GEMINI_API_KEY.isEmpty()) {
            outputTextArea.setText(" Error: Please set your Google Gemini API Key in the code.");
            return;
        }

        outputTextArea.setText("Translating... Please wait.");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + GEMINI_API_KEY);

                HttpURLConnection connHTTP = (HttpURLConnection) url.openConnection();
                connHTTP.setRequestMethod("POST");
                connHTTP.setRequestProperty("Content-Type", "application/json");
                connHTTP.setDoOutput(true);
                connHTTP.setConnectTimeout(10000);
                connHTTP.setReadTimeout(20000);

                String prompt = "Translate the following text from " + sourceLang + " to " + targetLang + ":\n\n" + input;

                String jsonPayload = String.format(
                    "{\"contents\": [{\"role\": \"user\", \"parts\": [{\"text\": \"%s\"}]}]}",
                    prompt.replace("\"", "\\\"").replace("\n", "\\n")
                );

                try (OutputStream os = connHTTP.getOutputStream()) {
                    os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = connHTTP.getResponseCode();
                StringBuilder response = new StringBuilder();
                
                if (responseCode >= 200 && responseCode < 300) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(connHTTP.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line);
                        }
                    }
                } else {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(connHTTP.getErrorStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line);
                        }
                    }
                    throw new IOException("API request failed with response code: " + responseCode + ". Error: " + response.toString());
                }

                String rawJsonResponse = response.toString();
                String translatedText = "";

                int textKeyIndex = rawJsonResponse.indexOf("\"text\": \"");
                if (textKeyIndex != -1) {
                    int textValueStartIndex = textKeyIndex + "\"text\": \"".length();
                    int textValueEndIndex = rawJsonResponse.indexOf("\"", textValueStartIndex);
                    if (textValueEndIndex != -1) {
                        translatedText = rawJsonResponse.substring(textValueStartIndex, textValueEndIndex);
                        
                        translatedText = translatedText.replace("\\n", "\n")
                                                     .replace("\\\"", "\"")
                                                     .replace("\\t", "\t")
                                                     .replace("\\r", "\r")
                                                     .replace("\\\\", "\\"); 
                    }
                }
                

                if (translatedText.isEmpty()) {
                    
                    throw new IOException("Could not extract translated text from response. " +
                                          "Raw API response: " + rawJsonResponse);
                }

                return translatedText;

            }

            @Override
            protected void done() {
                try {
                    String translated = get();
                    outputTextArea.setText(translated);
                    String sourceLang = languageMap.get(sourceLangBox.getSelectedItem().toString());
                    String targetLang = languageMap.get(targetLangBox.getSelectedItem().toString());
                    String input = inputTextArea.getText().trim();
                    saveTranslation(sourceLang, targetLang, input, translated);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    outputTextArea.setText("Translation interrupted: " + e.getMessage());
                    e.printStackTrace();
                } catch (Exception ex) {
                    outputTextArea.setText("Translation failed: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }.execute();
    }

    private void saveTranslation(String sourceLang, String targetLang, String input, String output) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO translations (source_lang, target_lang, input_text, output_text) VALUES (?, ?, ?, ?)"
            );
            ps.setString(1, sourceLang);
            ps.setString(2, targetLang);
            ps.setString(3, input);
            ps.setString(4, output);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Failed to save translation history: " + e.getMessage(),
                "Database Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showHistory() {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM translations ORDER BY id DESC");

            StringBuilder history = new StringBuilder();
            if (!rs.isBeforeFirst()) {
                history.append("No translation history found.");
            } else {
                while (rs.next()) {
                    history.append("From ").append(rs.getString("source_lang"))
                           .append(" To ").append(rs.getString("target_lang"))
                           .append(":\n")
                           .append("→ ").append(rs.getString("input_text")).append("\n")
                           .append("= ").append(rs.getString("output_text")).append("\n\n");
                }
            }

            JTextArea historyArea = new JTextArea(history.toString());
            historyArea.setEditable(false);
            historyArea.setLineWrap(true);
            historyArea.setWrapStyleWord(true);

            JOptionPane.showMessageDialog(this,
                new JScrollPane(historyArea),
                "Translation History",
                JOptionPane.INFORMATION_MESSAGE);

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Failed to retrieve translation history: " + e.getMessage(),
                "Database History Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LanguageTranslatorApp::new);
    }
}