/**
 * This is absolutely incomplete and not usable on the module path. But we require even such an incomplete module
 * definition so that the SPI gets access to the java compiler which is now a module not by default available on the
 * java 9 classpath anymore.
 */
module org.revapi.java.spi {
    requires java.compiler;
}