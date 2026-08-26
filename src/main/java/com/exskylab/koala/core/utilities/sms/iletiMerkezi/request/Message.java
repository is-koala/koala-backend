package com.exskylab.koala.core.utilities.sms.iletiMerkezi.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Message {

    private String text;

    private Recipients recipients;

}
