package protobuf;

import java.io.File;
import java.util.ArrayList;

public interface ProtobufProvider<T> {
    void generateCountryFile();

    T findCountryByName(File file, String countryName);

    ArrayList<String> findCountryByPoint(File file, double lat, double lon);
}
