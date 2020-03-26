package pt.tecnico.model;

public enum Parameters {
    PUBLICKEY(0),
    ACTION(1),
    GENERAL_NUMBER(2), MESSAGE(2),
    ANNOUNCMENTS(3);

    private final int index;

    Parameters(int i) {
        this.index = i;
    }

    public int getIndex() {
        return index;
    }
}
