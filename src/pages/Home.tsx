import { StyleSheet, Text, View } from 'react-native'
import React from 'react'
import SimpleUsb from '../components/SimpleUsb'
export default function Home() {
    return (
        <View style={styles.container}>
            <SimpleUsb/>
        </View>
    )
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: '#fff',
        alignItems: 'center',
        justifyContent: 'space-around',
    },
})