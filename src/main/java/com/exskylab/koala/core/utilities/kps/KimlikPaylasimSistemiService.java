package com.exskylab.koala.core.utilities.kps;

import java.util.Locale;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Service
public class KimlikPaylasimSistemiService {

    private static final Locale TURKISH = Locale.of("tr", "TR");


    private final RestTemplate restTemplate;

    public KimlikPaylasimSistemiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean kimlikDogrula(String tcNumber, String name, String surname, LocalDate birthDate, String tcDocumentNumber) {

        String kpsUrl = "https://tckimlik.nvi.gov.tr/service/kpspublicv2.asmx";

        int birthDay = birthDate.getDayOfMonth();
        int birthMonth = birthDate.getMonthValue();
        int birthYear = birthDate.getYear();


        String xmlBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soap:Body>"
                + "<KisiVeCuzdanDogrula xmlns=\"http://tckimlik.nvi.gov.tr/WS\">"
                + "<TCKimlikNo>" + tcNumber + "</TCKimlikNo>"
                + "<Ad>" + name.toUpperCase(TURKISH) + "</Ad>"
                + "<Soyad>" + surname.toUpperCase(TURKISH) + "</Soyad>"
                + "<SoyadYok>false</SoyadYok>"
                + "<DogumGun>" + birthDay + "</DogumGun>"
                + "<DogumGunYok>false</DogumGunYok>"
                + "<DogumAy>" + birthMonth + "</DogumAy>"
                + "<DogumAyYok>false</DogumAyYok>"
                + "<DogumYil>" + birthYear + "</DogumYil>"
                + "<TCKKSeriNo>" + tcDocumentNumber + "</TCKKSeriNo>"
                + "</KisiVeCuzdanDogrula>"
                + "</soap:Body>"
                + "</soap:Envelope>";


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "xml", StandardCharsets.UTF_8));
        headers.add("SOAPAction", "http://tckimlik.nvi.gov.tr/WS/KisiVeCuzdanDogrula");

        HttpEntity<String> entity = new HttpEntity<>(xmlBody, headers);

        String response = restTemplate.postForObject(kpsUrl, entity, String.class);

        return response != null && response.contains("<KisiVeCuzdanDogrulaResult>true</KisiVeCuzdanDogrulaResult>");



    }



}
