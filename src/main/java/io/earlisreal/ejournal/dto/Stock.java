package io.earlisreal.ejournal.dto;

import io.earlisreal.ejournal.util.Country;

import java.time.LocalDate;

public class Stock {

    private String code;
    private String name;
    private String companyId;
    private String securityId;
    private double price;
    private LocalDate lastDate;
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

    public LocalDate getLastDate() {
        return lastDate;
    }

    public Country getCountry() {
        return country;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    public void setLastDate(LocalDate lastDate) {
        this.lastDate = lastDate;
    }

    @Override
    public String toString() {
        return "Stock{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", companyId='" + companyId + '\'' +
                ", securityId='" + securityId + '\'' +
                ", price=" + price +
                ", lastDate=" + lastDate +
                ", country='" + country + '\'' +
                '}';
    }

}
