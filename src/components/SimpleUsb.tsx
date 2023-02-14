import React from 'react';
import {NativeModules, Button} from 'react-native';

const {ReactNativeSimpleUsb} = NativeModules;

const NewModuleButton = () => {
    const listUsbDevices = async () => {
        try {
            const device = await ReactNativeSimpleUsb.listUsbDevices();
            console.log(device);
            
        } catch (error) {
            console.error(error);
        }
    };

    const ConnectUsbDevices = async () => {
        try {
            const device = await ReactNativeSimpleUsb.ConnectUsbDevices(12346, 16385);
            console.log(device);
            
        } catch (error) {
            console.error(error);
        }
    }

    return (
        <>
            <Button
                title="List USB Devices"
                color="#432212"
                onPress={listUsbDevices}
            />
            <Button
                title="Connect to USB Devices"
                color="#702375"
                onPress={ConnectUsbDevices}
            />
        </>
    );
};

export default NewModuleButton;