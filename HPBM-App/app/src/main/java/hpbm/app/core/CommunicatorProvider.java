package hpbm.app.core;

public final class CommunicatorProvider {

    private static Communicator communicator;

    public static void setCommunicator(Communicator communicator) {
        CommunicatorProvider.communicator = communicator;
    }

    public static Communicator getCommunicator() {
        return communicator;
    }
}
