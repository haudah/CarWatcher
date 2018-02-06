package com.aramco.carwatcher;

public interface GetAddressListener
{
    public void onResponse(String address);

    public void onErrorResponse();
}
