/*
 * In-Game Account Switcher is a mod for Minecraft that allows you to change your logged in account in-game, without restarting Minecraft.
 * Copyright (C) 2015-2022 The_Fireplace
 * Copyright (C) 2021-2026 VidTu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>
 */

package ru.vidtu.ias.tests;

import ru.vidtu.ias.auth.cookie.CookieParser;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone parser regression tests. No JUnit, no Minecraft classes:
 * compiles with plain {@code javac} against the Gradle-cached annotations
 * jars and runs with plain {@code java}. See {@code tests/run-parser-tests}.
 * <p>
 * Must be launched with the repository root as the working directory so the
 * {@code .agents/skills} example files resolve.
 */
public final class CookieParserTest {
    private static final String EXAMPLES = ".agents/skills/refresh-and-cookie-alts/examples/";
    private static final String INVALID_KEY = "ias.error.cookie.invalid";

    private CookieParserTest() {
        throw new AssertionError("No instances.");
    }

    public static void main(String[] args) throws Exception {
        List<String> failures = new ArrayList<>();
        run("bareMcaJwtFallsThrough", CookieParserTest::bareMcaJwtFallsThrough, failures);
        run("inlineMcaJwtFallsThrough", CookieParserTest::inlineMcaJwtFallsThrough, failures);
        run("bearerMcaFallsThrough", CookieParserTest::bearerMcaFallsThrough, failures);
        run("jsonMcaFallsThrough", CookieParserTest::jsonMcaFallsThrough, failures);
        run("realisticRefreshAccepted", CookieParserTest::realisticRefreshAccepted, failures);
        run("localtsFileAccepted", CookieParserTest::localtsFileAccepted, failures);
        run("fullNetscapeAccepted", CookieParserTest::fullNetscapeAccepted, failures);
        run("cookieHeaderAccepted", CookieParserTest::cookieHeaderAccepted, failures);
        run("rejectsGarbage", CookieParserTest::rejectsGarbage, failures);
        run("rejectsHeaderWithoutAuth", CookieParserTest::rejectsHeaderWithoutAuth, failures);

        System.out.println((10 - failures.size()) + "/10 tests passed.");
        if (!failures.isEmpty()) {
            failures.forEach(f -> System.err.println("FAIL " + f));
            System.exit(1);
        }
    }

    private static void run(String name, ThrowingRunnable test, List<String> failures) {
        try {
            test.run();
            System.out.println("PASS " + name);
        } catch (Throwable t) {
            failures.add(name + ": " + t.getMessage());
        }
    }

    /** MCA JWTs must NOT be swallowed as refresh tokens; they fall through to TokenImporter. */
    private static void bareMcaJwtFallsThrough() throws Exception {
        String jwt = Files.readString(Path.of(EXAMPLES + "minecraft-access-token-jwt.txt")).strip();
        assertInvalid(jwt);
    }

    private static void inlineMcaJwtFallsThrough() {
        assertInvalid("eyJhbGciOiJSUzI1NiJ9.eyJ4dWlkIjoiMDAwMDAwMDAwMDAwMDAwMCJ9.c2ln");
    }

    private static void bearerMcaFallsThrough() throws Exception {
        String jwt = Files.readString(Path.of(EXAMPLES + "minecraft-access-token-jwt.txt")).strip();
        assertInvalid("Bearer " + jwt);
    }

    /** JSON wrappers are unwrapped by TokenImporter (MC_TOKEN_JSON), never the cookie parser. */
    private static void jsonMcaFallsThrough() throws Exception {
        String jwt = Files.readString(Path.of(EXAMPLES + "minecraft-access-token-jwt.txt")).strip();
        assertInvalid("{\"mcToken\":\"" + jwt + "\"}");
    }

    private static void realisticRefreshAccepted() throws Exception {
        String text = Files.readString(Path.of(EXAMPLES + "refresh-token-direct.txt"));
        CookieParser.ParsedCookies parsed = CookieParser.fromText(text);
        assertTrue(parsed.cookies().isEmpty(), "refresh file must yield no cookies");
        assertTrue(parsed.refreshToken().startsWith("M.C"), "refresh token must keep M.C prefix");
        assertTrue(parsed.refreshToken().length() >= 300, "example refresh token must be realistic length, was " + parsed.refreshToken().length());
    }

    private static void localtsFileAccepted() throws Exception {
        String text = Files.readString(Path.of(EXAMPLES + "refresh-token-localts.txt"));
        CookieParser.ParsedCookies parsed = CookieParser.fromText(text);
        assertTrue(parsed.cookies().isEmpty(), "localts file must yield no cookies");
        assertTrue(parsed.refreshToken().startsWith("M.C"), "username prefix must be stripped, M.C must remain");
        assertTrue(!parsed.refreshToken().contains(":"), "username prefix must be stripped");
    }

    private static void fullNetscapeAccepted() throws Exception {
        String text = Files.readString(Path.of(EXAMPLES + "cookie-alt-netscape.txt"));
        CookieParser.ParsedCookies parsed = CookieParser.fromText(text);
        assertTrue(parsed.cookies().size() == 14, "full export must yield 14 cookies, was " + parsed.cookies().size());
        // By design, __Host-MSAAUTHP embeds an M.C artifact that CookieParser
        // surfaces as the refresh token (extractEmbeddedRefresh).
        assertTrue(parsed.refreshToken().equals("M.C000_EXAMPLE.FAKE-PLACEHOLDER-NOT-A-REAL-COOKIE"),
                "embedded refresh must come from __Host-MSAAUTHP, was " + parsed.refreshToken());
        boolean hasAuth = parsed.cookies().values().stream().anyMatch(c -> c.name().equals("__Host-MSAAUTHP"));
        assertTrue(hasAuth, "export must contain __Host-MSAAUTHP");
        assertTrue(parsed.toSisuCookieHeader().contains("__Host-MSAAUTHP="), "SISU header must carry auth cookie");
    }

    private static void cookieHeaderAccepted() {
        CookieParser.ParsedCookies parsed = CookieParser.fromText("__Host-MSAAUTH=abc; __Host-MSAAUTHP=def; other=1");
        assertTrue(parsed.cookies().size() == 3, "header must yield 3 cookies");
        assertTrue(parsed.refreshToken().isBlank(), "header must yield no refresh token");
    }

    private static void rejectsGarbage() {
        assertInvalid("not a supported account format");
    }

    private static void rejectsHeaderWithoutAuth() {
        assertInvalid("ordinary=value; second=value");
    }

    private static void assertInvalid(String text) {
        try {
            CookieParser.ParsedCookies parsed = CookieParser.fromText(text);
            throw new AssertionError("expected " + INVALID_KEY + " but parsed as refresh=" + !parsed.refreshToken().isBlank());
        } catch (FriendlyException e) {
            assertTrue(INVALID_KEY.equals(e.key()), "wrong error key: " + e.key());
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
