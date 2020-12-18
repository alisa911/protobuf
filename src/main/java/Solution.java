import protobuf.ProtobufProviderImpl;

public class Solution {

    public static void main(String[] args) {

        ProtobufProviderImpl protobufProvider = new ProtobufProviderImpl();
        protobufProvider.generateCountryFile();
        protobufProvider.findCountryByName(ProtobufProviderImpl.RESULT_FILE, "belarus");
    }


}
