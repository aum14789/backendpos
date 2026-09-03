package main

import (
	"database/sql"
	"log"
)

// SeedDatabase triggers cloud sync from Firebase/Cloud backend if the device is activated
func SeedDatabase(db *sql.DB) error {
	ident, err := GetDeviceIdentity(db)
	if err != nil || ident == nil {
		log.Println("[Seeder] Device is not yet activated. Awaiting Device Activation Token before syncing Firebase data.")
		return nil
	}

	return SeedBranchCatalog(db, ident.BranchID, ident.CompanyID, ident.BranchName, ident.BranchCode, ident.DeviceID, ident.DeviceName, ident.DeviceCode)
}

// SeedBranchCatalog registers the branch & device identity and triggers real-time Firebase/Cloud sync
func SeedBranchCatalog(db *sql.DB, branchID, companyID, branchName, branchCode, deviceID, deviceName, deviceCode string) error {
	if companyID == "" {
		companyID = "comp-001"
	}
	if branchID == "" {
		branchID = "branch-001"
	}

	// 1. Ensure Cached Branch Identity
	_, _ = db.Exec(`
		INSERT OR REPLACE INTO cached_branches (branch_id, company_id, name, code, business_day_close_time, tax_rate, service_charge_rate)
		VALUES (?, ?, ?, ?, ?, ?, ?)
	`, branchID, companyID, branchName, branchCode, "02:00", 7.0, 0.0)

	// 2. Ensure Cached Device Identity
	_, _ = db.Exec(`
		INSERT OR REPLACE INTO cached_devices (device_id, branch_id, device_name, device_code, device_type)
		VALUES (?, ?, ?, ?, ?)
	`, deviceID, branchID, deviceName, deviceCode, "POS_MAIN")

	// 3. Trigger immediate cloud sync pull to populate live tables, menus, buffet, promotions, and users from Firebase/Cloud
	if GlobalSyncEngine != nil {
		GlobalSyncEngine.mu.Lock()
		GlobalSyncEngine.branchID = branchID
		GlobalSyncEngine.deviceID = deviceID
		GlobalSyncEngine.mu.Unlock()

		GlobalSyncEngine.TriggerSync()
	}

	log.Printf("[Seeder] Branch identity for '%s' (%s) configured. Triggered live sync pull from Firebase/Cloud backend.", branchName, branchID)
	return nil
}
