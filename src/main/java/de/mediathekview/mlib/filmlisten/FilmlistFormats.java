package de.mediathekview.mlib.filmlisten;

public enum FilmlistFormats {
    
    JSON("Json","json"),
    OLD_JSON("Old Json","json"),
    JSON_COMPRESSED("Json + XZ", "xz"),
    OLD_JSON_COMPRESSED("Old Json compressed","json");
    
    private String description;
    private String fileExtension;
    
    FilmlistFormats(String aDescription, String aFileExtension)
    {
      description=aDescription;
      fileExtension=aFileExtension;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getFileExtension() {
        return fileExtension;
    }
}