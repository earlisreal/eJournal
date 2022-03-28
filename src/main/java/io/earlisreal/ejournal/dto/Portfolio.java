package io.earlisreal.ejournal.dto;

public class Portfolio {

    private Integer id;
    private String name;

    public Portfolio(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Portfolio(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
