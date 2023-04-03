package io.earlisreal.ejournal.dto;

import io.earlisreal.ejournal.util.Country;

import java.util.Objects;

public class Stock {

    private String code;
    private String name;
    private String companyId;
    private String securityId;
    private double price;
    private Country country;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getSecurityId() {
        return securityId;
    }

    public void setSecurityId(String securityId) {
        this.securityId = securityId;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Country getCountry() {
        return country;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    @Override
    public String toString() {
        return "Stock{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", companyId='" + companyId + '\'' +
                ", securityId='" + securityId + '\'' +
                ", price=" + price +
                ", country='" + country + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stock stock = (Stock) o;
        return code.equals(stock.code) && country == stock.country;
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, country);
    }

    public String toCsv() {
        return code + "," + name + "," + (companyId == null ? "" : companyId) + ","
                + (securityId == null ? "" : securityId) + "," + price + "," + country;
    }

}
