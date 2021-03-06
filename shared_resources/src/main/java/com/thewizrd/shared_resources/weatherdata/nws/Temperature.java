package com.thewizrd.shared_resources.weatherdata.nws;

import com.google.gson.annotations.SerializedName;
import com.vimeo.stag.UseStag;

@UseStag(UseStag.FieldOption.ALL)
public class Temperature {

    @SerializedName("unitCode")
    private String unitCode;

    @SerializedName("qualityControl")
    private String qualityControl;

    @SerializedName("value")
    private Float value;

    public void setUnitCode(String unitCode) {
        this.unitCode = unitCode;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public void setQualityControl(String qualityControl) {
        this.qualityControl = qualityControl;
    }

    public String getQualityControl() {
        return qualityControl;
    }

    public void setValue(Float value) {
        this.value = value;
    }

    public Float getValue() {
        return value;
    }
}