package com.edusync.edusync.backend.admin.service;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class TemporaryPasswordGenerator {

    private static final String UPPERCASE =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final String LOWERCASE =
            "abcdefghijklmnopqrstuvwxyz";

    private static final String NUMBERS =
            "0123456789";

    private static final String SYMBOLS =
            "!@#$%&*";

    private static final String ALL_CHARACTERS =
            UPPERCASE + LOWERCASE + NUMBERS + SYMBOLS;

    private static final int PASSWORD_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {

        char[] password = new char[PASSWORD_LENGTH];

        password[0] = randomCharacter(UPPERCASE);
        password[1] = randomCharacter(LOWERCASE);
        password[2] = randomCharacter(NUMBERS);
        password[3] = randomCharacter(SYMBOLS);

        for (int i = 4; i < PASSWORD_LENGTH; i++) {
            password[i] = randomCharacter(ALL_CHARACTERS);
        }

        for (int i = password.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);

            char temp = password[i];
            password[i] = password[j];
            password[j] = temp;
        }

        return new String(password);
    }

    private char randomCharacter(String characters) {
        int index = secureRandom.nextInt(characters.length());
        return characters.charAt(index);
    }
}
