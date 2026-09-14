package com.uimatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Strict JSON and field checks; no reflection-based population or silent defaults. */
final class DefinitionJson
{
    private static final Pattern TEST_MARKER = Pattern.compile("(?i)(^|[^a-z])(synthetic|test|fixture)([^a-z]|$)");
    private final JsonObject object;
    private final String path;

    private DefinitionJson(JsonElement element, String path)
    {
        require(element.isJsonObject(), path + ": expected object");
        this.object = element.getAsJsonObject();
        this.path = path;
    }

    static DefinitionJson read(Reader input) throws IOException
    {
        JsonReader reader = new JsonReader(input);
        reader.setLenient(false);
        JsonElement element = readValue(reader, 0);
        require(reader.peek() == JsonToken.END_DOCUMENT, "$: trailing content");
        return new DefinitionJson(element, "$");
    }

    private static JsonElement readValue(JsonReader reader, int depth) throws IOException
    {
        require(depth < 32, reader.getPath() + ": nesting limit exceeded");
        JsonToken token = reader.peek();
        if (token == JsonToken.BEGIN_OBJECT)
        {
            JsonObject object = new JsonObject();
            reader.beginObject();
            while (reader.hasNext())
            {
                String name = reader.nextName();
                require(!object.has(name), reader.getPath() + ": duplicate field " + name);
                object.add(name, readValue(reader, depth + 1));
            }
            reader.endObject();
            return object;
        }
        if (token == JsonToken.BEGIN_ARRAY)
        {
            JsonArray array = new JsonArray();
            reader.beginArray();
            while (reader.hasNext())
            {
                array.add(readValue(reader, depth + 1));
            }
            reader.endArray();
            return array;
        }
        if (token == JsonToken.STRING)
        {
            return new JsonPrimitive(reader.nextString());
        }
        if (token == JsonToken.NUMBER)
        {
            return new JsonPrimitive(new BigDecimal(reader.nextString()));
        }
        if (token == JsonToken.BOOLEAN)
        {
            return new JsonPrimitive(reader.nextBoolean());
        }
        throw new IllegalArgumentException(reader.getPath() + ": expected non-null JSON value");
    }

    DefinitionJson fields(String... names)
    {
        Set<String> expected = new HashSet<>(Arrays.asList(names));
        Set<String> actual = new HashSet<>(object.keySet());
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        actual.removeAll(expected);
        require(missing.isEmpty() && actual.isEmpty(), path + ": missing fields " + missing + "; unknown fields " + actual);
        return this;
    }

    boolean has(String name)
    {
        return object.has(name);
    }

    void rejectMarkers()
    {
        rejectMarkers(object, path);
    }

    private static void rejectMarkers(JsonElement value, String path)
    {
        if (value.isJsonObject())
        {
            value.getAsJsonObject().entrySet().forEach(entry -> rejectMarkers(entry.getValue(), path + "." + entry.getKey()));
        }
        else if (value.isJsonArray())
        {
            value.getAsJsonArray().forEach(entry -> rejectMarkers(entry, path + "[]"));
        }
        else if (value.getAsJsonPrimitive().isString())
        {
            require(!TEST_MARKER.matcher(value.getAsString()).find(), path + ": production contains test marker");
        }
    }

    String text(String name)
    {
        JsonElement value = object.get(name);
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), at(name) + ": expected string");
        String text = value.getAsString();
        require(!text.isBlank(), at(name) + ": expected nonblank string");
        return text;
    }

    boolean bool(String name)
    {
        JsonElement value = object.get(name);
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(), at(name) + ": expected boolean");
        return value.getAsBoolean();
    }

    double number(String name, double minimum, double maximum)
    {
        JsonElement value = object.get(name);
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), at(name) + ": expected number");
        double number = value.getAsDouble();
        require(Double.isFinite(number) && number >= minimum && number <= maximum, at(name) + ": number out of range");
        return number;
    }

    int integer(String name, int maximum)
    {
        double value = number(name, 0, maximum);
        require(value == Math.rint(value), at(name) + ": expected integer");
        return (int) value;
    }

    <E extends Enum<E>> E choice(String name, Class<E> type)
    {
        String value = text(name);
        try
        {
            return Enum.valueOf(type, value);
        }
        catch (IllegalArgumentException exception)
        {
            throw new IllegalArgumentException(at(name) + ": unsupported value " + value, exception);
        }
    }

    DefinitionJson child(String name)
    {
        return new DefinitionJson(object.get(name), at(name));
    }

    <T> List<T> list(String name, Function<DefinitionJson, T> parse)
    {
        JsonElement value = object.get(name);
        require(value.isJsonArray(), at(name) + ": expected array");
        List<T> values = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray())
        {
            values.add(parse.apply(new DefinitionJson(element, at(name) + "[" + values.size() + "]")));
        }
        return List.copyOf(values);
    }

    String at(String name)
    {
        return path + "." + name;
    }

    static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new IllegalArgumentException(message);
        }
    }
}
