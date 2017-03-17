/*
 * Copyright 2015 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.jboss.dmr.ModelNode;

/**
 * A utility class for JSON files. The JSON specification doesn't allow comments but the extension's
 * configuration is likely to contain comments so that users can "annotate" what individual configuration items mean.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JSONUtil {

    private enum State {
        NORMAL, FIRST_SLASH, SINGLE_LINE, MULTI_LINE, STAR_IN_MULTI_LINE, IN_STRING, ESCAPE_IN_STRING
    }


    private JSONUtil() {

    }

    /**
     * @param json the JSON-encoded data
     * @param charset the charset of the data
     * @return an input stream that strips comments from json data provided as an input stream.
     */
    public static InputStream stripComments(InputStream json, Charset charset) {
        Reader rdr = stripComments(new InputStreamReader(json, Charset.forName("UTF-8")));
        return new ReaderInputStream(rdr, charset);
    }

    /**
     * @param json the JSON-encoded data
     * @return a String with comments stripped from the provided json data.
     */
    public static String stripComments(String json) {
        try {
            try (Reader rdr = stripComments(new StringReader(json))) {
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
            throw new AssertionError("IOException in StringReader? I thought that was impossible!", e);
        }
    }

    /**
     * @param json the JSON-encoded data
     * @return a reader that strips comments from json data provided as a reader.
     */
    public static Reader stripComments(final Reader json) {
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
                        case '"':
                            state = State.IN_STRING;
                            //intentional fallthrough
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
                        case '"':
                            emit = '/';
                            state = State.IN_STRING;
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
                        default: break;
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
                    case IN_STRING:
                        switch (lastChar) {
                        case '\\':
                            state = State.ESCAPE_IN_STRING;
                            break;
                        case '"':
                            state = State.NORMAL;
                            break;
                        }
                        if (lastChar != -1) {
                            cont = false;
                            lastChar = -1;
                        }
                        break;
                    case ESCAPE_IN_STRING:
                        if (lastChar != -1) {
                            cont = false;
                            lastChar = -1;
                            state = State.IN_STRING;
                        }
                        break;
                    }

                    if (cont) {
                        int ci = json.read();

                        if (ci == -1) {
                            //the end of input.. emit something if we're in an emittable state..
                            lastChar = -1;

                            switch (state) {
                                case SINGLE_LINE: case MULTI_LINE: case STAR_IN_MULTI_LINE:
                                    return -1;
                                default:
                                    return emit;
                            }
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

    /**
     * Converts the provided javascript object to JSON string.
     *
     * <p>If the object is a Map instance, it is stringified as key-value pairs, if it is a list, it is stringified as
     * a list, otherwise the object is merely converted to string using the {@code toString()} method.
     *
     * @param object the object to stringify.
     *
     * @return the object as a JSON string
     */
    public static String stringifyJavascriptObject(Object object) {
        StringBuilder bld = new StringBuilder();
        stringify(object, bld);
        return bld.toString();
    }

    public static ModelNode toModelNode(Object object) {
        ModelNode ret = new ModelNode();

        if (object instanceof Map) {
            ret.setEmptyObject();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) object).entrySet()) {
                String key = e.getKey().toString();
                try {
                    //noinspection ResultOfMethodCallIgnored
                    int idx = Integer.parseInt(key);
                    if (!ret.has(0)) {
                        boolean isMap = false;
                        try {
                            ret.keys();
                        } catch (IllegalArgumentException ignored) {
                            isMap = true;
                        }

                        if (isMap) {
                            throw new IllegalArgumentException("Mixed javascript list and object not supported.");
                        }
                        ret.setEmptyList();
                    }

                    ret.get(idx).set(toModelNode(e.getValue()));
                } catch (NumberFormatException ignored) {
                    ret.get(e.getKey().toString()).set(toModelNode(e.getValue()));
                }
            }
        } else if (object instanceof List) {
            ret.setEmptyList();
            for (Object o : (List<?>) object) {
                ret.add(toModelNode(o));
            }
        } else if (object instanceof Integer) {
            ret.set((Integer) object);
        } else if (object instanceof Double) {
            ret.set((Double) object);
        } else if (object instanceof Boolean) {
            ret.set((Boolean) object);
        } else if (object instanceof Long) {
            ret.set((Long) object);
        } else if (object instanceof String) {
            ret.set((String) object);
        } else if (object instanceof BigDecimal) {
            ret.set((BigDecimal) object);
        } else if (object instanceof BigInteger) {
            ret.set((BigInteger) object);
        } else if (object != null) {
            ret.set(object.toString());
        }

        return ret;
    }

    private static void stringify(Object object, StringBuilder bld) {
        if (object instanceof Map) {
            bld.append("{");
            @SuppressWarnings("unchecked")
            Iterator<Map.Entry> it = ((Map) object).entrySet().iterator();
            if (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                bld.append("\"").append(e.getKey()).append("\":");
                stringify(e.getValue(), bld);
            }

            while (it.hasNext()) {
                bld.append(",");
                Map.Entry<?, ?> e = it.next();
                bld.append("\"").append(e.getKey()).append("\":");
                stringify(e.getValue(), bld);
            }

            bld.append("}");
        } else if (object instanceof List) {
            bld.append("[");
            Iterator<?> it = ((List<?>) object).iterator();

            if (it.hasNext()) {
                stringify(it.next(), bld);
            }

            while (it.hasNext()) {
                bld.append(",");
                stringify(it.next(), bld);
            }

            bld.append("]");
        } else if (object == null) {
            bld.append("null");
        } else {
            bld.append(object.toString());
        }
    }
}
