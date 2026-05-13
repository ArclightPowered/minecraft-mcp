package io.izzel.minecraftmcp.json;

import java.util.*;

public final class Json {
    private Json() {}

    public static Object parse(String text) {
        return new Parser(text).parse();
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object value, StringBuilder out) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof String s) { writeString(s, out); return; }
        if (value instanceof Number || value instanceof Boolean) { out.append(value); return; }
        if (value instanceof Map<?, ?> map) {
            out.append('{'); boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) out.append(','); first = false;
                writeString(String.valueOf(e.getKey()), out); out.append(':'); write(e.getValue(), out);
            }
            out.append('}'); return;
        }
        if (value instanceof Iterable<?> iterable) {
            out.append('['); boolean first = true;
            for (Object item : iterable) { if (!first) out.append(','); first = false; write(item, out); }
            out.append(']'); return;
        }
        if (value.getClass().isArray()) {
            out.append('['); int n = java.lang.reflect.Array.getLength(value);
            for (int i=0;i<n;i++) { if (i>0) out.append(','); write(java.lang.reflect.Array.get(value, i), out); }
            out.append(']'); return;
        }
        writeString(String.valueOf(value), out);
    }

    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            switch(c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String s; private int i;
        Parser(String s) { this.s = Objects.requireNonNull(s); }
        Object parse() { Object v=value(); ws(); if (i!=s.length()) throw err("Trailing input"); return v; }
        private Object value() { ws(); if (i>=s.length()) throw err("Expected value"); char c=s.charAt(i);
            return switch(c) { case '{' -> object(); case '[' -> array(); case '"' -> string(); case 't' -> lit("true", Boolean.TRUE); case 'f' -> lit("false", Boolean.FALSE); case 'n' -> lit("null", null); default -> { if (c=='-' || Character.isDigit(c)) yield number(); throw err("Expected value"); } };
        }
        private Map<String,Object> object(){ i++; Map<String,Object> m=new LinkedHashMap<>(); ws(); if(peek('}')){i++;return m;} while(true){ ws(); if(!peek('"')) throw err("Expected object key"); String k=string(); ws(); expect(':'); Object v=value(); m.put(k,v); ws(); if(peek('}')){i++;return m;} expect(','); }}
        private List<Object> array(){ i++; List<Object> a=new ArrayList<>(); ws(); if(peek(']')){i++;return a;} while(true){ a.add(value()); ws(); if(peek(']')){i++;return a;} expect(','); }}
        private String string(){ expect('"'); StringBuilder b=new StringBuilder(); while(i<s.length()){ char c=s.charAt(i++); if(c=='"') return b.toString(); if(c=='\\'){ if(i>=s.length()) throw err("Bad escape"); char e=s.charAt(i++); switch(e){ case '"' -> b.append('"'); case '\\' -> b.append('\\'); case '/' -> b.append('/'); case 'b' -> b.append('\b'); case 'f' -> b.append('\f'); case 'n' -> b.append('\n'); case 'r' -> b.append('\r'); case 't' -> b.append('\t'); case 'u' -> { if(i+4>s.length()) throw err("Bad unicode escape"); b.append((char)Integer.parseInt(s.substring(i,i+4),16)); i+=4; } default -> throw err("Bad escape"); }} else b.append(c);} throw err("Unterminated string"); }
        private Object number(){ int start=i; if(peek('-')) i++; while(i<s.length()&&Character.isDigit(s.charAt(i))) i++; boolean fp=false; if(peek('.')){fp=true;i++; while(i<s.length()&&Character.isDigit(s.charAt(i))) i++;} if(i<s.length()&&(s.charAt(i)=='e'||s.charAt(i)=='E')){fp=true;i++; if(i<s.length()&&(s.charAt(i)=='+'||s.charAt(i)=='-')) i++; while(i<s.length()&&Character.isDigit(s.charAt(i))) i++;} String n=s.substring(start,i); if (fp) return Double.parseDouble(n); long value = Long.parseLong(n); return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (int) value : value; }
        private Object lit(String l,Object v){ if(!s.startsWith(l,i)) throw err("Expected "+l); i+=l.length(); return v; }
        private void ws(){ while(i<s.length()&&Character.isWhitespace(s.charAt(i))) i++; }
        private boolean peek(char c){ return i<s.length()&&s.charAt(i)==c; }
        private void expect(char c){ if(!peek(c)) throw err("Expected '"+c+"'"); i++; }
        private IllegalArgumentException err(String msg){ return new IllegalArgumentException(msg+" at "+i); }
    }
}
