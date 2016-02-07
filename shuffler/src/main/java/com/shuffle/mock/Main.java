package com.shuffle.mock;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Implementation using mock addresses.
 *
 * Created by Daniel Krawisz on 2/3/16.
 */
public class Main {

    private Main() {}

    static String readFile(String path, Charset encoding)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    public static void main(String args) {
        String fileName = "config.json";

        try {
            String content = readFile(fileName, Charset.defaultCharset());
        } catch (IOException e) {
            return;
        }


    }
}