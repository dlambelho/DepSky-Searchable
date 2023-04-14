package org.schemes.booleanCash;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import javax.crypto.NoSuchPaddingException;
import org.schemes.crypto.CryptoPrimitives;
import org.schemes.database.DatabaseConnection;
import org.schemes.parser.TextExtractPar;
import org.schemes.parser.TextProc;

public class BXTSearch {

    private static final String INSERT_BXTTSET = "INSERT INTO CLUSION.BXT_TSET (WORD , FILE_ENC) VALUES (?, ?)";
    private static final String INSERT_BXTXSET = "INSERT INTO CLUSION.BXT_XSET (CROSS_TAG) VALUES (?)";

    private static final String SELECT_BXTTSET = "SELECT FILE_ENC FROM CLUSION.BXT_TSET WHERE WORD = ?";
    private static final String SELECT_BXTXSET = "SELECT COUNT(*) FROM CLUSION.BXT_XSET WHERE CROSS_TAG = ?";

    private static final String UPDATE_BXTTSET = "UPDATE CLUSION.BXT_TSET SET FILE WHERE WORD LIKE ?";
    public static SecureRandom random = new SecureRandom();
    public static int sizeOfFileIdentifer = 150;
    static Multimap<byte[], byte[]> Tset = ArrayListMultimap.create();
    static byte[] keyS = new byte[32];
    static byte[] keyX = new byte[32];
    HashSet<String> xSet;

    public static List<byte[]> setup(Multimap<String, String> lookup)
            throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException {

        byte[] keyS = new byte[32];
        byte[] keyX = new byte[32];

        for (String word : lookup.keySet()) {
            byte[] keyE = CryptoPrimitives.generateHmac(keyS, word);
            byte[] xtrap = CryptoPrimitives.generateHmac(keyX, word);


            byte[] iv1 = new byte[16];

            byte[] encWord = CryptoPrimitives.encryptAES_CTR_String(keyE, iv1, word, sizeOfFileIdentifer);

            for (String file : lookup.get(word)) {
                byte[] iv = new byte[16];
                random.nextBytes(iv);

                byte[] encF = CryptoPrimitives.encryptAES_CTR_String(keyE, iv, file, sizeOfFileIdentifer);
                byte[] xtag = CryptoPrimitives.generateHmac(xtrap, file);

                Tset.put(encWord, encF);

                try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(INSERT_BXTTSET)) {
                    statement.setString(1, new String(Base64.getEncoder().encode(encWord)));
                    statement.setBytes(2, Base64.getEncoder().encode(encF));
                    statement.execute();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(INSERT_BXTXSET)) {
                    statement.setString(1, new String(Base64.getEncoder().encode(xtag)));
                    statement.execute();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return List.of(keyS, keyX);
    }

    public static List<byte[]> setup(List<List<String>> allLines, List<String> files)
            throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException {

        TextExtractPar.extractWords(allLines, files);

        return setup(TextExtractPar.lp1);
    }

    public static void search(List<String> keywords)
            throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException {
        String firstWord = keywords.remove(0);

        byte[] keyE = CryptoPrimitives.generateHmac(keyS, firstWord);

        byte[] iv1 = new byte[16];

        byte[] encWord = CryptoPrimitives.encryptAES_CTR_String(keyE, iv1, firstWord, sizeOfFileIdentifer);

        List<byte[]> encFiles = new ArrayList<>();

        ResultSet result;
        try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(SELECT_BXTTSET)) {
            statement.setString(1, new String(Base64.getEncoder().encode(encWord)));
            result = statement.executeQuery();

            while (result.next()) {
                encFiles.add(Base64.getDecoder().decode(result.getBytes(1)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        List<String> files = new ArrayList<>();
        for (byte[] encFile : encFiles) {
            files.add(new String(CryptoPrimitives.decryptAES_CTR_String(encFile, keyE), StandardCharsets.UTF_8).trim());
        }
        Set<String> resultSet = new HashSet<>(files);
        for (String word : keywords) {
            byte[] xtrap = CryptoPrimitives.generateHmac(keyX, word);

            for (String file : files) {
                byte[] xtag = CryptoPrimitives.generateHmac(xtrap, file);

                try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(SELECT_BXTXSET)) {
                    statement.setString(1, new String(Base64.getEncoder().encode(xtag)));
                    result = statement.executeQuery();

                    if (result.next()) {
                        if (result.getInt(1) <= 0) {
                            resultSet.remove(file);
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        System.out.println(resultSet.stream().sorted().toList());
    }

    public static void main(String[] args)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IOException, NoSuchAlgorithmException,
            InvalidKeySpecException, InvalidKeyException, NoSuchProviderException {
        String pathName = "../texts/business";

        ArrayList<File> listOfFile = new ArrayList<File>();

        TextProc.listf(pathName, listOfFile);

        TextProc.TextProc(false, pathName);

        setup(TextExtractPar.lp1);

        ArrayList<String> keywords;

        Scanner in = new Scanner(System.in);
        String option;

        do {
            System.out.print("> ");
            option = in.nextLine().trim().toLowerCase();
            String[] conjunctions = option.split(" ");

            if (!option.equals("exit")) {
                keywords = new ArrayList<>(List.of(conjunctions));

                search(keywords);
            }

        } while (!option.equals("exit"));

    }
}
