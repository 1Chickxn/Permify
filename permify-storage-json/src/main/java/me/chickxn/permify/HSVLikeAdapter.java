package me.chickxn.permify;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.kyori.adventure.util.HSVLike;

import java.io.IOException;

public class HSVLikeAdapter extends TypeAdapter<HSVLike> {
    @Override
    public void write(JsonWriter out, HSVLike value) throws IOException {
        // Serialize HSVLike to JSON
        out.beginObject();
        out.name("h").value(value.h());
        out.name("s").value(value.s());
        out.name("v").value(value.v());
        out.endObject();
    }

    @Override
    public HSVLike read(JsonReader in) throws IOException {
        // Deserialize JSON to HSVLikeImpl (or the concrete class)
        in.beginObject();
        float h = 0, s = 0, v = 0;
        while (in.hasNext()) {
            String field = in.nextName();
            switch (field) {
                case "h" -> h = (float) in.nextDouble();
                case "s" -> s = (float) in.nextDouble();
                case "v" -> v = (float) in.nextDouble();
                default -> in.skipValue();
            }
        }
        in.endObject();
        return HSVLike.hsvLike(h, s, v); // Use the factory method from Adventure
    }
}