package org.revapi.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;

import javax.annotation.Nonnull;

/**
 * A utility class to strip comments from JSON files. The JSON specification doesn't allow comments but the extension's
 * configuration is likely to contain comments so that users can "annotate" what individual configuration items mean.
 * <p/>
 * This class can be used to strip such comments from the JSON before it is passed to {@link org.jboss.dmr.ModelNode}
 * factory methods to create the DMR representation of the JSON configuration.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public final class StripCommentsFromJSON {

    private enum State {
        NORMAL, FIRST_SLASH, SINGLE_LINE, MULTI_LINE, STAR_IN_MULTI_LINE
    }


    private StripCommentsFromJSON() {

    }

    public static InputStream stream(InputStream json, Charset charset) {
        Reader rdr = reader(new InputStreamReader(json));
        return new ReaderInputStream(rdr, charset);
    }

    public static String string(String json) {
        try {
            try (Reader rdr = reader(new StringReader(json))) {
                StringBuilder bld = new StringBuilder();

                char[] buf = new char[1024];
                int cnt;

                while ((cnt = rdr.read(buf)) != -1) {
                    bld.append(buf, 0, cnt);
                }

                return bld.toString();
            }
        } catch (IOException e) {
            //doesn't happen with strings
            return null;
        }
    }

    public static Reader reader(final Reader json) {
        return new Reader() {

            State state = State.NORMAL;
            int lastChar = -1;

            @Override
            public int read() throws IOException {
                while (true) {
                    boolean cont = true;
                    int emit = lastChar;

                    switch (state) {
                    case NORMAL:
                        switch (lastChar) {
                        case '/':
                            state = State.FIRST_SLASH;
                            break;
                        default:
                            if (lastChar != -1) {
                                lastChar = -1;
                                cont = false;
                            }
                        }
                        break;
                    case FIRST_SLASH:
                        switch (lastChar) {
                        case '/':
                            state = State.SINGLE_LINE;
                            break;
                        case '*':
                            state = State.MULTI_LINE;
                            break;
                        default:
                            emit = '/';
                            state = State.NORMAL;
                            cont = false;
                        }
                        break;
                    case SINGLE_LINE:
                        switch (lastChar) {
                        case '\n':
                            state = State.NORMAL;
                            lastChar = -1;
                            cont = false;
                            break;
                        }
                        break;
                    case MULTI_LINE:
                        switch (lastChar) {
                        case '*':
                            state = State.STAR_IN_MULTI_LINE;
                            break;
                        }
                        break;
                    case STAR_IN_MULTI_LINE:
                        switch (lastChar) {
                        case '/':
                            state = State.NORMAL;
                            break;
                        }
                        break;
                    }

                    if (cont) {
                        int ci = json.read();

                        if (ci == -1) {
                            lastChar = -1;
                            return emit;
                        }

                        lastChar = ci;
                    } else {
                        return emit;
                    }
                }
            }

            @Override
            public int read(@Nonnull char[] cbuf, int off, int len) throws IOException {
                for (int i = 0; i < len; ++i) {
                    int c = read();

                    if (c == -1) {
                        return i == 0 ? -1 : i;
                    }

                    cbuf[off + i] = (char) c;
                }

                return len;
            }

            @Override
            public void close() throws IOException {
                json.close();
            }
        };
    }

}
