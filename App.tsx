import { StyleSheet, Text, View, Button } from 'react-native'
import { NativeModules, NativeEventEmitter  } from 'react-native'
import React, { useEffect } from 'react'

const {CalendarModule} = NativeModules;

const eventEmitter = new NativeEventEmitter(CalendarModule); 

export default function App() {

	useEffect(() => {
		eventEmitter.addListener('Event Count', (event) => {
			console.log(event);
		})
		return () => {
			eventEmitter.removeAllListeners("Event Count");
		}
	}, [])


	//logs every method
    console.log(CalendarModule);
	
	// CalendarModule.createCalendarEvent();
	const handlePromise = async (res: string) => {
		try {
			const result = await CalendarModule.createCalendarPromise();
			console.log(result);
		}catch (e) {
			console.error(e);
		}
	}

	const handleSendPromise = async () => {
		try {
			const result = await CalendarModule.callDeviceDiscovery("what it is that it is");
			console.log(result);
		} catch (error) {
			console.log(error);
		}
	}

	return (
      	<View style={styles.conteiner}>
    		<Text>App</Text>

			<Button
				title="Create Event"
				onPress={() => CalendarModule.createCalendarEvent()}
			/>
			<Button
				title="Create Callback"
				onPress={() => CalendarModule.createCalendarCallback((res:string)=>console.log(res))}
			/>
			<Button
				title="Send Promise"
				onPress={handleSendPromise}
			/>
			<Button
				title="Create Promise"
				onPress={handlePromise}
			/>
     	</View>
    )
}

const styles = StyleSheet.create({
	conteiner: {
		flex: 1,
		backgroundColor: '#fff',
		alignItems: 'center',
		justifyContent: 'space-evenly',
	},
})