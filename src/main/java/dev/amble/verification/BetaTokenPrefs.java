package dev.amble.verification;

import java.util.prefs.Preferences;

import com.google.gson.Gson;

public class BetaTokenPrefs {

    private final Preferences prefs;
    private final String key;

    private Boolean verified = null;

    public BetaTokenPrefs(Class<?> clazz, String modId) {
        this.prefs = Preferences.userNodeForPackage(clazz);
        this.key = modId + "-jwt";
    }

    public void verify() {
        verified = true;
    }

    public void saveToken(String jwt) {
        prefs.put(key, jwt);
        verified = null;
    }

    public String loadToken() {
        verified = null;
        return prefs.get(key, null);
    }

    public void clearToken() {
        prefs.remove(key);
        verified = null;
    }

    public boolean isTokenValid() {
        if (verified != null) return verified;

        String jwt = loadToken();
        if (jwt == null) return false;

        try {
            return verified = BetaVerification.downloadAsString(BetaVerification.SERVER_DATA.verifier() + "/verify",
                            builder -> builder.header("Authorization", "Bearer " + jwt))
                    .thenApply(s -> new Gson().fromJson(s, VerificationResponse.class))
                    .thenApply(VerificationResponse::valid).get();
        } catch (Exception e) {
            BetaVerification.LOGGER.error("Failed to check token validity", e);
            return false;
        }
    }

    record VerificationResponse(boolean valid, String error) { }
}