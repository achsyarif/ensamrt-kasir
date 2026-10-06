# PHP Backend API Script for Transaction Settlement (Pelunasan)

Upload this script to your InfinityFree server under `https://achsyarif.rf.gd/kasir/api/transaksi.php` (handling `action=pelunasan`) or as a standalone `pelanggan_api.artifact.md` / `pelunasan.php`.

```php
<?php
/**
 * PHP Backend API Script for Transaction Settlement (Pelunasan)
 * Endpoint: https://achsyarif.rf.gd/kasir/api/transaksi.php?action=pelunasan
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

// Database Connection Configuration (InfinityFree / MySQL)
$host = "sqlxxx.infinityfree.com"; // Replace with your MySQL hostname
$username = "if0_xxxxxxxx";         // Replace with your MySQL username
$password = "your_password";         // Replace with your MySQL password
$database = "if0_xxxxxxxx_kasir";    // Replace with your MySQL database name

$conn = new mysqli($host, $username, $password, $database);

if ($conn->connect_error) {
    echo json_encode([
        "status" => "error",
        "message" => "Database connection failed: " . $conn->connect_error
    ]);
    exit();
}

$action = isset($_GET['action']) ? $_GET['action'] : '';

if ($action === 'pelunasan' || basename($_SERVER['PHP_SELF']) === 'pelunasan.php') {
    $inputData = json_decode(file_get_contents('php://input'), true);

    if (!$inputData) {
        $inputData = $_POST;
    }

    $id = isset($inputData['id']) ? intval($inputData['id']) : 0;
    $metode = isset($inputData['metode']) ? $conn->real_escape_string($inputData['metode']) : 'TUNAI';
    $uang_dp = isset($inputData['uang_dp']) ? floatval($inputData['uang_dp']) : (isset($inputData['dp']) ? floatval($inputData['dp']) : 0.0);
    $sisa = isset($inputData['sisa']) ? floatval($inputData['sisa']) : 0.0;

    if ($id <= 0) {
        echo json_encode([
            "status" => "error",
            "message" => "Invalid transaction ID"
        ]);
        exit();
    }

    $stmtCheck = $conn->prepare("SELECT total_harga FROM transaksi WHERE id = ?");
    $stmtCheck->bind_param("i", $id);
    $stmtCheck->execute();
    $resultCheck = $stmtCheck->get_result();

    if ($resultCheck->num_rows === 0) {
        echo json_encode([
            "status" => "error",
            "message" => "Transaction not found"
        ]);
        exit();
    }

    $row = $resultCheck->fetch_assoc();
    $total_harga = floatval($row['total_harga']);
    $stmtCheck->close();

    $bayar = (strtoupper($metode) === 'DP') ? $uang_dp : $total_harga;
    $kembali = ($bayar > $total_harga) ? ($bayar - $total_harga) : 0.0;
    $updated_at = round(microtime(true) * 1000);

    $stmtUpdate = $conn->prepare("UPDATE transaksi SET metode_pembayaran = ?, bayar = ?, uang_bayar = ?, dp = ?, sisa = ?, kembali = ?, updated_at = ? WHERE id = ?");
    $stmtUpdate->bind_param("sdddddii", $metode, $bayar, $bayar, $uang_dp, $sisa, $kembali, $updated_at, $id);

    if ($stmtUpdate->execute()) {
        echo json_encode([
            "status" => "success",
            "message" => "Transaction settlement updated successfully",
            "id" => $id,
            "metode" => $metode,
            "uang_dp" => $uang_dp,
            "sisa" => $sisa
        ]);
    } else {
        echo json_encode([
            "status" => "error",
            "message" => "Failed to update transaction: " . $stmtUpdate->error
        ]);
    }

    $stmtUpdate->close();
} else {
    echo json_encode([
        "status" => "error",
        "message" => "Invalid action or endpoint"
    ]);
}

$conn->close();
?>
```
