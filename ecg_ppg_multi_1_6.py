import serial
import matplotlib.pyplot as plt
import time

# Измерване на 1kHz
# Запис на време, сърдечна честота, систолично и диастолично налягане

fig = plt.figure(figsize=(20, 5))
fig.subplots_adjust(bottom=0.1, left=0.1)

ax = plt.subplot(321)
ax0 = plt.subplot(322, sharex=ax)
ax1 = plt.subplot(323, sharex=ax)
ax10 = plt.subplot(324, sharex=ax)
ax11 = plt.subplot(325, sharex=ax)

Pserial = 0
Cserial = 0

y1all, y2all = [], []
y1fall, y2fall = [], []

point_times = []
hr_ecg_rr, hr_ppg_rr = [], []
peak_np, peak_pn = [], []

# Get data from device and make the necessary processing
def receive_adc(num_sets=1, ress=0):
    global point_times, hr_ecg_rr, hr_ppg_rr, peak_np, peak_pn
    global y1all, y2all, y1fall, y2fall

    y1all.clear()
    y2all.clear()
    y1fall.clear()
    y2fall.clear()

    # ppg
    D1 = 60
    N1 = 11.0
    av1 = 29

    #  ecg
    D2 = 20
    N2 = 7.0

    timing = 0
    x_point = 0
    point_last = 0
    all_time = 2500 * 4

    y1, y2 = [], []
    y1f, y2f = [], []
    x1 = []

    Pserial.flushInput()
    Pserial.flush()
    print("set " + str(x_point + 1) + ' is being taken ...')

    while True:
        while Pserial.inWaiting() == 0:
            pass
        val = str(Pserial.readline())
        if (val[2] == 's') | (val[-4] == 's'):
            inv1 = int(Pserial.read(1)[-1])
            inv2 = int(Pserial.read(1)[-1])
            inv3 = int(Pserial.read(1)[-1])
            val1 = (inv1 << 8) | inv2
            inv1 = int(Pserial.read(1)[-1])
            inv2 = int(Pserial.read(1)[-1])
            inv3 = int(Pserial.read(1)[-1])
            val2 = (inv1 << 8) | inv2
            x1.append(timing)
            y1.append(val1)
            y2.append(val2)
            y1all.append(val1)
            y2all.append(val2)
            timing += 1
            if x1.__len__() == all_time:
                print("set " + str(x_point + 1) + ' taken')
                x_point += 1
                # ppg work
                y1f = filter_avg(filter_dxn(y1, D1, N1), av1)
                # ecg work
                y2f = filter_dxn(y2, D2, N2)
                y1fall += y1f
                y2fall += y2f
                # peak detection
                find_adc(y1f, y2f)

                if x_point < num_sets:
                    x1.clear()
                    y1.clear()
                    y2.clear()
                    y1f.clear()
                    y2f.clear()
                    timing = 0
                    print("set " + str(x_point + 1) + ' is being taken ...')
                if x_point == num_sets:
                    print('\n')
                    Cserial.write(b'p')
                    if ress == 0:
                        return

                    point_times.sort()
                    print(point_times)
                    point_times.pop(0)
                    point_times.pop(-1)
                    print(point_times)
                    point_last = (sum(point_times) / float(point_times.__len__()))
                    print('PAT: ' + "{:.3f}".format(point_last) + " ms" + '\n')

                    # Za moment ne
                    # peak_np.sort()
                    # print(peak_np)
                    # peak_np.pop(0)
                    # peak_np.pop(-1)
                    # print(peak_np)
                    # tim_np = (sum(peak_np) / float(peak_np.__len__()))
                    # print('N -> P: ' + "{:.3f}".format(tim_np) + " ms" + '\n')
                    # peak_pn.sort()
                    # print(peak_pn)
                    # peak_pn.pop(0)
                    # peak_pn.pop(-1)
                    # print(peak_pn)
                    # tim_pn = sum(peak_pn) / float(peak_pn.__len__())
                    # print('P -> N: ' + "{:.3f}".format(tim_pn) + " ms" + '\n')

                    print(hr_ecg_rr)
                    hr_ecg_rr.sort()
                    hr_ecg_rr.pop(0)
                    hr_ecg_rr.pop(-1)
                    print(hr_ecg_rr)
                    bp_rb_ecg = sum(hr_ecg_rr) / hr_ecg_rr.__len__()
                    print('HR ecg: ' + "{:.3f}".format(bp_rb_ecg))
                    print(' ')

                    print(hr_ppg_rr)
                    hr_ppg_rr.sort()
                    hr_ppg_rr.pop(0)
                    hr_ppg_rr.pop(-1)
                    print(hr_ppg_rr)
                    bp_rb_ppg = sum(hr_ppg_rr) / hr_ppg_rr.__len__()
                    print('HR ppg: ' + "{:.3f}".format(bp_rb_ppg))
                    print(' ')
                    print('Predicted HR: ' + "{:.3f}".format((bp_rb_ppg + bp_rb_ecg) / 2))
                    print(' ')
                    print('Blood pressure: ' + "{:.3f}".format(calculate_bp(point_last,
                                                        ((bp_rb_ppg + bp_rb_ecg) / 2),
                                                        122)))
                    print(' ')
                    # bp_sys = int(input('Write SYS: '))
                    # bp_dia = int(input('Write DIA: '))
                    # file = open('log_1k_data.txt', 'a')
                    # file.write(str(bp_sys) + '\t' +
                    #            str(bp_dia) + '\t' +
                    #            "{:.3f}".format(bp_rb) + '\t' +
                    #            "{:.3f}".format(point_last) + '\n')
                    # file.close()

                    return
                Pserial.flushInput()
                Pserial.flush()
        Pserial.flush()

# FilterDxN to remove noise and DC
def filter_dxn(list_in, coef_d, coef_n):
    moment_y = []
    y_out = []
    doz = coef_d * int(coef_n) - coef_d
    filt_dn = []
    for i in range(doz + 1):
        filt_dn.append(0.0)
    lengy = list_in.__len__()
    for i in range(lengy):
        filt_dn.pop(0)
        filt_dn.append(list_in[i])
        sum_filter = 0.0
        for f in range(0, doz + 1, coef_d):
            sum_filter = sum_filter + filt_dn[f]
        moment_y.append(filt_dn[int(doz / 2)] - (sum_filter / coef_n))
    moment_y.reverse()
    filt_dn.clear()
    for i in range(doz + 1):
        filt_dn.append(0.0)
    lengy = moment_y.__len__()
    for i in range(lengy):
        filt_dn.pop(0)
        filt_dn.append(moment_y[i])
        sum_filter = 0.0
        for f in range(0, doz + 1, coef_d):
            sum_filter = sum_filter + filt_dn[f]
        y_out.append(filt_dn[int(doz / 2)] - (sum_filter / coef_n))
    y_out.reverse()
    return y_out

# Averaging filter to clean up the signal
def filter_avg(list_in, coef_a):
    moment_y = []
    y_out = []
    filt_avg = []
    if (coef_a % 2) == 0:
        for i in range(coef_a + 1):
            filt_avg.append(0.0)
        lengy = list_in.__len__()
        for i in range(lengy - coef_a):
            filt_avg.pop(0)
            filt_avg.append(list_in[i])
            moment_y.append((filt_avg[0] / 2.0 + sum(filt_avg[0:-1]) + filt_avg[-1] / 2.0) / float(coef_a))
        moment_y.reverse()
        filt_avg.clear()
        for i in range(coef_a + 1):
            filt_avg.append(0.0)
        lengy = moment_y.__len__()
        for i in range(lengy - coef_a):
            filt_avg.pop(0)
            filt_avg.append(moment_y[i])
            y_out.append((filt_avg[0] / 2.0 + sum(filt_avg[0:-1]) + filt_avg[-1] / 2.0) / float(coef_a))
    else:
        for i in range(coef_a):
            filt_avg.append(0.0)
        lengy = list_in.__len__()
        for i in range(lengy - coef_a):
            filt_avg.pop(0)
            filt_avg.append(list_in[i])
            moment_y.append(sum(filt_avg) / float(coef_a))
        moment_y.reverse()
        filt_avg.clear()
        for i in range(coef_a):
            filt_avg.append(0.0)
        lengy = moment_y.__len__()
        for i in range(lengy - coef_a):
            filt_avg.pop(0)
            filt_avg.append(moment_y[i])
            y_out.append(sum(filt_avg) / float(coef_a))
    y_out.reverse()
    sta = y_out[0]
    for i in range(int(coef_a * 1.5)):
        y_out.insert(0, sta)
    return y_out

# Process data and get results
def find_adc(list_in1, list_in2):
    global point_times, hr_ecg_rr, hr_ppg_rr

    dot_y1, dot_y2 = [], []
    hr_ecg_tim, hr_ppg_tim = [], []
    count_peak = []
    coef_ppg = 0.6
    coef_ecg = 0.7
    num = 0
    pea = 0
    dot_mid = 9
    dot_range = (dot_mid * 2) + 1

    y1_detect = list_in1.copy()
    y2_detect = list_in2.copy()

    adc_peaks_isolate(y1_detect, coef_ppg, 1)
    adc_peaks_isolate(y2_detect, coef_ecg)

    hr_ecg_tim = adc_peaks_sort(y1_detect)
    hr_ppg_tim = adc_peaks_sort(y2_detect)

    if y2_detect.__len__() < y1_detect.__len__():
        leny_detect = y2_detect.__len__()
    else:
        leny_detect = y1_detect.__len__()
    for ic in range(leny_detect):
        if ic >= 100:

            if (y1_detect[ic] > 1.0) & (pea == 1000):
                if num > 20:
                    count_peak.append(num + 1)
                num = 0
                pea = 0

            if (y2_detect[ic] > 1.0) & (pea == 0):
                pea = 1000

            if pea > 0:
                num += 1

    count_peak.sort()
    print(count_peak)
    if count_peak.__len__() > 0:
        ic = 0
        while True:
            if ic == count_peak.__len__():
                break
            if (count_peak[ic] < 245) | (count_peak[ic] > 450):
                count_peak.pop(ic)
                ic = 0
                continue
            ic += 1
        adc_peaks_arrange(count_peak, factor=0)
    print(count_peak)
    if count_peak.__len__() != 0:
        ptt_tim = (sum(count_peak) / float(count_peak.__len__()))
        if ptt_tim < 500.0:
            point_times.append(ptt_tim)
    else:
        print("No legitimate peaks found!")
    adc_peaks_negative(y1_detect)

    adc_peaks_arrange(hr_ecg_tim, 30, 0)
    adc_peaks_arrange(hr_ppg_tim, 30, 0)

    hr_ecg_rr.append(60.0 * (1000.0 / (sum(hr_ecg_tim) / hr_ecg_tim.__len__())))
    hr_ppg_rr.append(60.0 * (1000.0 / (sum(hr_ppg_tim) / hr_ppg_tim.__len__())))

# Isolate only the peaks from signal; if negt = 1 the negative peaks will also be isolated
def adc_peaks_isolate(inp_list, coef=0.0, negt=0):
    leny_detect = inp_list.__len__()
    max1 = max(inp_list[int(leny_detect / 2):]) * coef
    min1 = min(inp_list[int(leny_detect / 2):]) * coef
    max2 = max(inp_list[int(leny_detect / 2):]) * 0.29
    min2 = min(inp_list[int(leny_detect / 2):]) * 0.29
    is_above = 0
    is_below = 0
    start_point = 0

    for f in range(inp_list.__len__()):
        if (inp_list[f] > 0.0) & (inp_list[f] > max1) & (is_above == 0):
            is_above = 1
            start_point = f
        if (is_above == 1) & (inp_list[f] < max2):
            is_above = 0
            moment_max = max(inp_list[start_point:f])
            for t in range(start_point, f):
                if inp_list[t] == moment_max:
                    continue
                else:
                    inp_list[t] = 0.0
            start_point = 0

        if negt == 1:
            if (inp_list[f] < 0.0) & (inp_list[f] < min1) & (is_below == 0):
                is_below = 1
                start_point = f
            if (is_below == 1) & (inp_list[f] > min2):
                is_below = 0
                if (start_point < f) & (start_point > 0):
                    moment_min = min(inp_list[start_point:f])
                    for t in range(start_point, f):
                        if inp_list[t] == moment_min:
                            continue
                        else:
                            inp_list[t] = 0.0
                start_point = 0

        if (is_above == 0) & (is_below == 0):
            inp_list[f] = 0.0

# Remove peaks that are too close (artefacts or noise) and return avgr time between peaks
def adc_peaks_sort(inp_arr):
    peaks_rr = []
    peaks_num = 0
    peaks_start = 0

    for ic in range(inp_arr.__len__()):
        if (inp_arr[ic] > 0.0) & (peaks_start > 0):
            peaks_rr.append(peaks_num + 1)
            if (peaks_rr.__len__() > 1) & (peaks_rr[-1] < 250):
                inp_arr[ic] = 0.0
                peaks_rr.pop(-1)
            else:
                peaks_num = 0
        if (inp_arr[ic] > 0.0) & (peaks_start == 0):
            peaks_start = 100
        if peaks_start != 0:
            peaks_num += 1
    peaks_rr.sort()
    if peaks_rr.__len__() > 3:
        peaks_rr.pop(0)
        peaks_rr.pop(-1)
    return peaks_rr

# Arrange data to clean it from smaller/bigger values
def adc_peaks_arrange(inp_list, gran=30, factor=1):
    if inp_list.__len__() > 2:
        diff1 = inp_list[1] - inp_list[0]
        diff2 = inp_list[2] - inp_list[1]
        if (gran > diff2) & (gran <= diff1):
            inp_list.pop(0)
    count_peak_num = inp_list.__len__() - 1
    while inp_list.__len__() > 1:
        if count_peak_num == 0:
            break
        if (inp_list[count_peak_num] - inp_list[(count_peak_num - 1)]) >= gran:
            if factor == 0:
                inp_list.pop(count_peak_num)
            if factor == 1:
                inp_list.pop(count_peak_num - 1)
            count_peak_num = inp_list.__len__() - 1
            continue
        else:
            count_peak_num -= 1

# Calculate avrg time between positive and negative ppg peaks and vice versa
def adc_peaks_negative(inp_list):
    global peak_np, peak_pn

    peak_pos = []
    peak_neg = []
    peak_num = 0
    pn_set = 0
    np_set = 0

    for ic in range(inp_list.__len__()):
        if (inp_list[ic] > 1.0) & (pn_set == 0):
            pn_set = 1
            if np_set == 1:
                peak_neg.append(peak_num + 1)
                np_set = 0
            peak_num = 0
        if (inp_list[ic] < -1.0) & (pn_set == 1):
            peak_pos.append(peak_num + 1)
            peak_num = 0
            pn_set = 0
            np_set = 1
        if (pn_set == 1) | (np_set == 1):
            peak_num += 1

    peak_pos.sort()
    if peak_pos.__len__() > 0:
        if peak_pos.__len__() > 3:
            adc_peaks_arrange(peak_pos)
        peak_pn.append(sum(peak_pos) / float(peak_pos.__len__()))

    peak_neg.sort()
    if peak_neg.__len__() > 0:
        if peak_neg.__len__() > 3:
            adc_peaks_arrange(peak_neg)
        peak_np.append(sum(peak_neg) / float(peak_neg.__len__()))

# Calculate blood pressure
def calculate_bp(inp_pat, inp_hr, inp_bp_co):
    alfa = 0.2
    beta = 0.5
    gama = 0.2

    bp_now = (inp_pat * alfa) + (inp_hr * beta) + (inp_bp_co * gama)

    return bp_now

# Plot data
def plot_adc():
    ax.clear()
    ax0.clear()
    ax1.clear()
    ax10.clear()
    ax11.clear()

    ax.plot(y1all)
    ax0.plot(y2all)
    ax1.plot(y1fall, 'b')
    ax10.plot(y2fall, 'b')
    ax11.plot(y1fall, 'g', y2fall, 'r')

    ax.set_xlabel('time [ms]')
    ax0.set_xlabel('time [ms]')
    ax1.set_xlabel('time [ms]')
    ax10.set_xlabel('time [ms]')

    ax.set_ylabel('PPG')
    ax0.set_ylabel('ECG')
    ax1.set_xlabel('PPG filtered')
    ax10.set_xlabel('ECG filtered')

    ax.grid()
    ax0.grid()
    ax1.grid()
    ax10.grid()
    ax11.grid()


if __name__ == "__main__":
    print('Waiting')
    time.sleep(10)
    print('Measuring\n')
    ans = '/dev/ttyACM0'
    Pserial = serial.Serial(ans, 115200)
    Cserial = serial.Serial('/dev/ttyACM1', 115200)
    Cserial.write(b'o')
    receive_adc()
    plot_adc()
    plt.show()
    Pserial.close()
    Cserial.close()
    exit(0)
