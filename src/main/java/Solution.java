import protobuf.ProtobufProviderImpl;

public class Solution {

    public static void main(String[] args) {

        ProtobufProviderImpl protobufProvider = new ProtobufProviderImpl();
        protobufProvider.generateCountryFile();
        protobufProvider.findCountryByName(ProtobufProviderImpl.RESULT_FILE, "belarus");
        protobufProvider.findCountryByPoint(ProtobufProviderImpl.RESULT_FILE, 30.710000, 54.840000);
    }
}
