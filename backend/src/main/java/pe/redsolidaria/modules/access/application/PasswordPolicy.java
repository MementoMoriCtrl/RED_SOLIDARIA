package pe.redsolidaria.modules.access.application;

/** Shared password limits for the REST user-creation flow and the local bootstrap. */
public final class PasswordPolicy {
    public static final int MIN_CODE_POINTS = 12;
    public static final int MAX_CODE_POINTS = 128;
    public static final int MAX_BCRYPT_UTF8_BYTES = 72;

    private PasswordPolicy() {}

    public static void validate(CharSequence password) {
        if (password == null) throw new IllegalArgumentException("La contraseña es obligatoria");
        int codePoints = Character.codePointCount(password, 0, password.length());
        if (codePoints < MIN_CODE_POINTS || codePoints > MAX_CODE_POINTS) {
            throw new IllegalArgumentException("La contraseña debe tener entre 12 y 128 caracteres");
        }
        if (utf8Length(password) > MAX_BCRYPT_UTF8_BYTES) {
            throw new IllegalArgumentException("La contraseña excede el máximo seguro admitido por BCrypt");
        }
    }

    private static int utf8Length(CharSequence text) {
        int bytes = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = Character.codePointAt(text, i);
            bytes += codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
            i += Character.charCount(codePoint);
        }
        return bytes;
    }
}
