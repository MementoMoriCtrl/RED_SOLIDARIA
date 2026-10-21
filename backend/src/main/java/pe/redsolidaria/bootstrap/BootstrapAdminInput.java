package pe.redsolidaria.bootstrap;

public record BootstrapAdminInput(String correo, String nombre, String apellido, char[] password) {
    @Override public String toString() {
        return "BootstrapAdminInput[correo=" + correo + ", nombre=" + nombre + ", apellido=" + apellido + ", password=[REDACTED]]";
    }
}
