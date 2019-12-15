document.getElementById('container').onchange = function () {
    let bill = Number(document.getElementById('billTotal').value);
    let tipPercent = document.getElementById('tipInput').value;
    let split = document.getElementById('splitInput').value;
    let tipValue = bill * (tipPercent / 100)
    let newBillEach = (bill + tipValue) / split;
    let tipEach = tipValue / split;
    document.getElementById('tipOutput').innerHTML = tipPercent + "%";
    document.getElementById('splitOutput').innerHTML = split;
    document.getElementById('newBill').innerHTML = '$' + newBillEach.toFixed(2);
    document.getElementById('tipEach').innerHTML = '$' + tipEach.toFixed(2);
}
