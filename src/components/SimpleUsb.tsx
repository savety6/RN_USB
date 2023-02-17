import React from 'react';
import {NativeModules, Button} from 'react-native';

const {ReactNativeSimpleUsb} = NativeModules;

const NewModuleButton = () => {
    const listUsbDevices = async () => {
        try {
            const device = await ReactNativeSimpleUsb.listDevices();
            console.log(device);
            
        } catch (error) {
            console.error(error);
        }
    };

    const AskUsbDevicesPermission = async () => {
        try {
            const device = await ReactNativeSimpleUsb.requestPermission("/dev/bus/usb/001/003");
            console.log(device);

        } catch (error) {
            console.error(error);
        }
    }
    
    const ConnectUsbDevices = async () => {
        try {
            const device = await ReactNativeSimpleUsb.connect("/dev/bus/usb/001/003");
            console.log(device);
            
        } catch (error) {
            console.error(error);
        }
    }

    const DisonnectUsbDevices = async () => {
        try {
            const device = await ReactNativeSimpleUsb.disconnect();
            console.log(device);
            
        } catch (error) {
            console.error(error);
        }
    }

    const read = async () => {
        try {
            const device = await ReactNativeSimpleUsb.read();
            console.log(device);

        } catch (error) {
            console.error(error);
        }
    }

    const write = async () => {
        try {
            const device = await ReactNativeSimpleUsb.write("o");
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
                title="Ask USB Devices Permission"
                color="#432212"
                onPress={AskUsbDevicesPermission}
            />
            <Button
                title="Connect to USB Devices"
                color="#432212"
                onPress={ConnectUsbDevices}
            />
            <Button
                title="Disconnect to USB Devices"
                color="#432212"
                onPress={DisonnectUsbDevices}
            />
            <Button
                title="read from USB Devices"
                color="#432212"
                onPress={read}
            />
            <Button
                title="write to USB Devices"
                color="#432212"
                onPress={write}
            />
            
        </>
    );
};

export default NewModuleButton;