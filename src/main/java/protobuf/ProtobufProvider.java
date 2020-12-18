package protobuf;

import java.io.File;

public interface ProtobufProvider<T> {
    void generateCountryFile();

    T findCountryByName(File file, String countryName);
}
