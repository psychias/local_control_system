import java.io.*;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class Tig {
    private static final String REPO_DIR = "repo_java";
    private static final String STAGED_FILE = "staged.csv";
    private static final String COMMIT_FILE = "commits.csv";
    private static final String TIGIGNORE_FILE = ".tigignore";
    private static final int HASH_LEN = 16;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Tig <command> [args]");
            return;
        }

        String command = args[0];
        switch (command) {
            case "init":
                if (args.length < 2) {
                    System.err.println("Usage: java Tig init <directory>");
                    return;
                }
                init(args[1]);
                break;

            case "add":
                if (args.length < 2) {
                    System.err.println("Usage: java Tig add <file>");
                    return;
                }
                add(args[1]);
                break;

            case "commit":
                if (args.length < 2) {
                    System.err.println("Usage: java Tig commit <message>");
                    return;
                }
                commit(args[1]);
                break;

            case "log":
                if (args.length < 1) {
                    System.err.println("Usage: java Tig log [-num]");
                    return;
                } else if (args.length < 2) {
                    log();  
                } else {
                    log(args[1]); 
                }
                break;
            
      

            case "status":
                status(System.getProperty("user.dir"));
                break;

            case "checkout":
                if (args.length < 2) {
                    System.err.println("Usage: java Tig checkout <commit_id>");
                    return;
                }
                checkout(args[1]);
                break;

            case "diff":
                if (args.length < 2) {
                    System.err.println("Usage: java Tig diff <filename>");
                    return;
                }
                diff(args[1]);
                break;

            default:
                System.err.println("Unknown command: " + command);
        }
    }

    private static void init(String repoPathStr) throws IOException {
        Path repoPath = Paths.get(repoPathStr);
        Path tigDir = repoPath.resolve(".tig");
        if (!Files.exists(repoPath)) {
            Files.createDirectories(repoPath);
            System.out.println("Created repository directory: " + repoPath.toAbsolutePath());
        }
    
        if (!Files.exists(tigDir)) {
            Files.createDirectory(tigDir);
            System.out.println("Initialized empty repository in: " + tigDir.toAbsolutePath());
        }
    
        Path commitsFile = tigDir.resolve(COMMIT_FILE);
        if (!Files.exists(commitsFile)) {
            Files.createFile(commitsFile);
            System.out.println("Created commits file: " + commitsFile.toAbsolutePath());
        }
    }

    private static void add(String fileName) throws IOException {
        Path repoPath = Paths.get(System.getProperty("user.dir"));
        Path tigDir = repoPath.resolve(".tig");
        Path stagedFile = tigDir.resolve(STAGED_FILE);
        Path tigignorePath = tigDir.resolve(TIGIGNORE_FILE);

        Set<String> ignoredFiles = loadTigignore(tigignorePath);

        if (ignoredFiles.contains(fileName)) {
            System.err.println("Error: File is ignored: " + fileName);
            return;
        }

        Path filePath = repoPath.resolve(fileName);
        if (!Files.exists(filePath)) {
            System.err.println("Error: File does not exist: " + fileName);
            return;
        }

        String fileHash = calculateHash(filePath);
        Map<String, String> stagedFiles = loadCsvToMap(stagedFile.toString());
        stagedFiles.put(fileName, fileHash);
        saveMapToCsv(stagedFile.toString(), stagedFiles);

        System.out.println("File staged: " + fileName);
    }

    private static void commit(String message) throws IOException {
        Path repoPath = Paths.get(System.getProperty("user.dir"));
        Path tigDir = repoPath.resolve(".tig");
        Path stagedFile = tigDir.resolve(STAGED_FILE);
        Path commitsFile = tigDir.resolve(COMMIT_FILE);
    
        Map<String, String> stagedFiles = loadCsvToMap(stagedFile.toString());
        if (stagedFiles.isEmpty()) {
            System.err.println("No files staged for commit.");
            return;
        }
    
        String commitId = UUID.randomUUID().toString().substring(0, 16);
        Path commitDir = tigDir.resolve(commitId);
        Files.createDirectory(commitDir);
    
        for (Map.Entry<String, String> entry : stagedFiles.entrySet()) {
            Path sourcePath = repoPath.resolve(entry.getKey());
            Path destPath = commitDir.resolve(entry.getValue() + ".bck");
            Files.copy(sourcePath, destPath);
        }
    
        try (BufferedWriter writer = Files.newBufferedWriter(commitsFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String fileHashes = String.join(" ", stagedFiles.values());
            writer.write(String.join(",", commitId, Instant.now().toString(), message, String.join(" ", stagedFiles.keySet()), fileHashes));
            writer.newLine();
        }
    
        Files.delete(stagedFile);
        System.out.println("Committed changes with ID: " + commitId);
    }

    private static void status(String repoPathStr) throws IOException {
        Path repoPath = Paths.get(repoPathStr);
        Path tigDir = repoPath.resolve(".tig");
        Path stagedFile = tigDir.resolve(STAGED_FILE);
        Path tigignorePath = tigDir.resolve(TIGIGNORE_FILE);
        Path commitsFile = tigDir.resolve(COMMIT_FILE);
    
        Set<String> ignoredFiles = loadTigignore(tigignorePath);
        Map<String, String> stagedFiles = loadCsvToMap(stagedFile.toString());
        List<Path> workingFiles = Files.walk(repoPath)
                .filter(Files::isRegularFile)
                .filter(file -> !file.startsWith(tigDir))
                .collect(Collectors.toList());
    
        List<String> allCommittedFiles = new ArrayList<>();
        List<String> allCommittedHashes = new ArrayList<>();

        // Building commit state
        try (BufferedReader reader = Files.newBufferedReader(commitsFile)) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length > 2) {
                    allCommittedFiles.addAll(Arrays.asList(parts[3].split(" ")));
                    allCommittedHashes.addAll(Arrays.asList(parts[4].split(" ")));
                }
            }
        }

        Map<String, String> dictionary = new HashMap<>();
        Map<String, String> flippedDict = new HashMap<>();
        for (int i = 0; i < Math.min(allCommittedHashes.size(), allCommittedFiles.size()); i++) {
            dictionary.put(allCommittedHashes.get(i), allCommittedFiles.get(i));
        }
        for (Map.Entry<String, String> entry : dictionary.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            flippedDict.put(value, key);
        }

        List<String> committedHashes = new ArrayList<>(flippedDict.values());
        List<String> committedFiles = new ArrayList<>(flippedDict.keySet());
    
        for (Path file : workingFiles) {
            String relativePath = repoPath.relativize(file).toString();
            if (ignoredFiles.contains(relativePath)) {
                continue;
            }
            
            String fileHash = calculateHash(file);
            if (stagedFiles.containsKey(relativePath)) {
                if (stagedFiles.get(relativePath).equals(fileHash)) {
                    System.out.println("Staged: " + relativePath);
                } else {
                    System.out.println("Modified (staged): " + relativePath);
                }
            } else if (committedFiles.contains(relativePath)) {
                if (committedHashes.contains(fileHash)) {
                    System.out.println("Committed: " + relativePath);
                } else {
                    System.out.println("Modified and not staged: " + relativePath);
                }
            } else {
                System.out.println("Untracked: " + relativePath);
            }
        }
    }

    private static Set<String> loadTigignore(Path tigignorePath) throws IOException {
        if (!Files.exists(tigignorePath)) {
            return Collections.emptySet();
        }

        return Files.lines(tigignorePath)
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toSet());
    }

    private static void checkout(String commitId) throws IOException {
        Path repoPath = Paths.get(System.getProperty("user.dir"));
        Path tigDir = repoPath.resolve(".tig");
        Path commitDir = tigDir.resolve(commitId);
        Path commitsFile = tigDir.resolve(COMMIT_FILE);

        if (!Files.exists(commitDir)) {
            System.err.println("Error: Commit ID not found: " + commitId);
            return;
        }

        Files.list(commitDir).filter(Files::isRegularFile).forEach(file -> {
            try {
                List<String> committedFiles = new ArrayList<>();
                List<String> committedHashes = new ArrayList<>();

                try (BufferedReader reader = Files.newBufferedReader(commitsFile)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split(",");
                        if (parts.length > 0 && parts[0].equals(commitId)) {
                            committedFiles.addAll(Arrays.asList(parts[3].split(" ")));
                            committedHashes.addAll(Arrays.asList(parts[4].split(" ")));
                        }
                    }
                }
                Map<String, String> dictionary = new HashMap<>();
                for (int i = 0; i < Math.min(committedHashes.size(), committedFiles.size()); i++) {
                    dictionary.put(committedHashes.get(i), committedFiles.get(i));
                }
                String hashName = file.getFileName().toString().split("\\.")[0];
                String originalName = dictionary.get(hashName);
                Path destPath = repoPath.resolve(originalName);
                Files.copy(file, destPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        System.out.println("Checked out commit: " + commitId);
    }

    private static void log(String... args) throws IOException {
        Path currentPath = Paths.get(System.getProperty("user.dir"));
        
        if (!currentPath.endsWith(REPO_DIR)) {
            currentPath = currentPath.resolve(REPO_DIR);
        }
        
        Path tigDir = currentPath.resolve(".tig");
        Path commitsFile = tigDir.resolve(COMMIT_FILE);
    
    
        if (!Files.exists(commitsFile)) {
            System.out.println("there are no commits found.");
            return;
        }
    
        List<String> commits = Files.lines(commitsFile).collect(Collectors.toList());
    
        //commits.forEach(System.out::println);
    
        if (commits.isEmpty()) {
            System.out.println("No commits found.");
            return;
        }
    
        int n = 5; // default value
    
        if (args.length >= 1) {
            n = Integer.parseInt(args[0].substring(1)); 
        }
    
        int start = Math.max(0, commits.size() - n);
        for (int i = commits.size() - 1; i >= start; i--) {
            System.out.println();
            String[] parts = commits.get(i).split(",");
            System.out.println("Commit ID: " + parts[0]);
            System.out.println("Date: " + parts[1]);
            System.out.println("Message: " + parts[2]);
            System.out.println();
        }
    }
    
    private static void diff(String fileName) throws IOException {
        Path repoPath = Paths.get(System.getProperty("user.dir"));
        Path tigDir = repoPath.resolve(".tig");
        Path commitsFile = tigDir.resolve(COMMIT_FILE);
    
        if (!Files.exists(commitsFile)) {
            System.out.println("Error: There are no commits done. Commit a file first.");
            return;
        }
    
        List<String> commits = Files.readAllLines(commitsFile, StandardCharsets.UTF_8);
        String latestCommitId = null;
        String committedHash = null;
    
        for (int i = commits.size() - 1; i >= 0; i--) {
            String[] parts = commits.get(i).split(",");
            String commitId = parts[0];
            String[] files = parts[3].split(" ");
            String[] hashes = parts[4].split(" ");
    
            for (int j = 0; j < files.length; j++) {
                if (files[j].equals(fileName)) {
                    latestCommitId = commitId;
                    committedHash = hashes[j];
                    break;
                }
            }
    
            if (committedHash != null) {
                break;
            }
        }
    
        if (committedHash == null) {
            System.out.println("Error: The file: " + fileName + "  was not found in any commit.");
            return;
        }
    
        Path committedFilePath = tigDir.resolve(latestCommitId).resolve(committedHash + ".bck");
        Path currentFilePath = repoPath.resolve(fileName);
        List<String> committedLines;
        List<String> currentLines;
    
        // issue reading the file in utf-8, tried different formats 
        try {
            committedLines = Files.readAllLines(committedFilePath, StandardCharsets.UTF_8);
            currentLines = Files.readAllLines(currentFilePath, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            System.out.println("Issues found in UTF-8. Trying UTF-16.");
    
            try {
                committedLines = Files.readAllLines(committedFilePath, StandardCharsets.UTF_16);
                currentLines = Files.readAllLines(currentFilePath, StandardCharsets.UTF_16);
            } catch (MalformedInputException ex) {
                System.out.println("Issues found in UTF-16. Trying binary.");
    
                // if UTF-16 fails, read as binary
                committedLines = readAsBinaryLines(committedFilePath);
                currentLines = readAsBinaryLines(currentFilePath);
            }
        }
    
        generateUnifiedDiff(fileName, committedLines, currentLines);
    }
    
    private static void generateUnifiedDiff(String fileName, List<String> committedLines, List<String> currentLines) {
        int committedLength = committedLines.size();
        int currentLength = currentLines.size();
        int maxLength = Math.max(committedLength, currentLength);
    
        List<String> diffResult = new ArrayList<>();
        //int contextLines = 5; 
    
        for (int i = 0; i < maxLength; i++) {
            String committedLine = (i < committedLength) ? committedLines.get(i) : null;
            String currentLine = (i < currentLength) ? currentLines.get(i) : null;
    
            if (committedLine == null) {
                diffResult.add("+ Line " + (i + 1) + ": " + currentLine);
            } else if (currentLine == null) {
                diffResult.add("- Line " + (i + 1) + ": " + committedLine);
            } else if (!committedLine.equals(currentLine)) {
                diffResult.add("- Line " + (i + 1) + ": " + committedLine);
                diffResult.add("+ Line " + (i + 1) + ": " + currentLine);
            } else {
                if (i == 0 || i == maxLength - 1 || (!committedLines.get(i).equals(currentLines.get(i - 1)) && !committedLines.get(i).equals(currentLines.get(i + 1)))) {
                    diffResult.add("  Line " + (i + 1) + ": " + committedLine);
                }
            }
        }
    
        if (diffResult.isEmpty()) {
            System.out.println("There is no difference between the current file and the committed version.");
        } else {
            System.out.println("Diff between committed and current version of " + fileName + ":");
            diffResult.forEach(System.out::println);
        }
    }
    
    private static List<String> readAsBinaryLines(Path filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        byte[] fileContent = Files.readAllBytes(filePath);
        StringBuilder sb = new StringBuilder();
    
        for (byte b : fileContent) {
            sb.append(String.format("%02x ", b));
        }
    
        lines.add(sb.toString().trim());
        return lines;
    }

    
    // private static void diff(String fileName) throws IOException {
    //     Path repoPath = Paths.get(System.getProperty("user.dir"));
    //     Path tigPath = repoPath.resolve(".tig");

    //     List<Path> manifestFiles = Files.walk(tigPath)
    //             .filter(file -> file.toString().endsWith(".csv"))
    //             .collect(Collectors.toList());
    //     manifestFiles.sort((file1, file2) -> file2.compareTo(file1));

    //     Path latestManifest = manifestFiles.get(0);
    //     String committedHash = null;

    //     try (BufferedReader reader = Files.newBufferedReader(latestManifest)) {
    //         String line;
    //         while ((line = reader.readLine()) != null) {
    //             String[] parts = line.split(",");
    //             if (parts[0].equals(fileName)) {
    //                 committedHash = parts[1];
    //                 break;
    //             }
    //         }
    //     }

    //     if (committedHash == null) {
    //         System.out.println("File not found in any commit.");
    //         return;
    //     }

    //     Path committedDir = tigPath.resolve(committedHash);
    //     Path committedFile = committedDir.resolve(fileName);
    //     if (!Files.exists(committedFile)) {
    //         System.out.println("Committed file not found: " + committedFile);
    //         return;
    //     }

    //     Path currentFile = repoPath.resolve(fileName);
    //     if (!Files.exists(currentFile)) {
    //         System.out.println("Current file not found: " + currentFile);
    //         return;
    //     }

    //     try (BufferedReader committedReader = Files.newBufferedReader(committedFile);
    //          BufferedReader currentReader = Files.newBufferedReader(currentFile)) {
    //         String committedLine;
    //         String currentLine;
    //         while ((committedLine = committedReader.readLine()) != null) {
    //             currentLine = currentReader.readLine();
    //             if (currentLine == null) {
    //                 System.out.println(committedLine);
    //             } else if (!committedLine.equals(currentLine)) {
    //                 System.out.println(committedLine);
    //                 System.out.println("+" + currentLine);
    //             }
    //         }

    //         while ((currentLine = currentReader.readLine()) != null) {
    //             System.out.println("+" + currentLine);
    //         }
    //     }
    // }

    // private static void log(String num, String... args) throws IOException {
    //     Path commitsFile = Paths.get(System.getProperty("user.dir"), REPO_DIR, ".tig", COMMIT_FILE);
    //     List<String> commits = Files.lines(commitsFile).collect(Collectors.toList());

    //     int start = Math.max(0, commits.size() - Integer.parseInt(num));
    //     for (int i = commits.size() - 1; i >= start; i--) {
    //         String[] parts = commits.get(i).split(",");
    //         System.out.println("Commit ID: " + parts[0]);
    //         System.out.println("Date: " + parts[1]);
    //         System.out.println("Message: " + parts[2]);
    //         System.out.println();
    //     }
    // }

    private static String calculateHash(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            byte[] hashBytes = digest.digest(bytes);
            return bytesToHex(hashBytes, HASH_LEN);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Unable to calculate hash.", e);
        }
    }

    private static String bytesToHex(byte[] bytes, int length) {
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < length && i < bytes.length; i++) {
            String hex = Integer.toHexString(0xff & bytes[i]);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static Map<String, String> loadCsvToMap(String filename) throws IOException {
        // Path path = Paths.get(REPO_DIR, filename);
        Path path = Paths.get(filename);
        if (!Files.exists(path)) return new HashMap<>();

        Map<String, String> map = new HashMap<>();
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split(",");
            map.put(parts[0], parts[1]);
        }
        return map;
    }

    private static void saveMapToCsv(String filename, Map<String, String> map) throws IOException {
        // Path path = Paths.get(REPO_DIR, filename);
        Path path = Paths.get(filename);
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                writer.write(entry.getKey() + "," + entry.getValue());
                writer.newLine();
            }
        }
    }
}
