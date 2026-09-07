-- Fix BCrypt hashes for demo seed users so that pin-login and password authentication work
UPDATE users SET pin_code = '$2a$10$3n3nWX3a7salqcVriL.2.eVjGCbysLBhi0ReTThl26wy8IY8X5JCO', password_hash = '$2a$10$i50/pAXDdCj2u43dB7CHgezdtc4f0/DWMmVdVvlMHR/AhQwXgQFaa' WHERE username = 'cashier01';
UPDATE users SET pin_code = '$2a$10$RJXiYED7ihmJuEBYPfHcjuim.jmkALcVARGOXQcm7tl3PAuhur4gK', password_hash = '$2a$10$i50/pAXDdCj2u43dB7CHgezdtc4f0/DWMmVdVvlMHR/AhQwXgQFaa' WHERE username = 'manager01';
UPDATE users SET pin_code = '$2a$10$3n3nWX3a7salqcVriL.2.eVjGCbysLBhi0ReTThl26wy8IY8X5JCO', password_hash = '$2a$10$i50/pAXDdCj2u43dB7CHgezdtc4f0/DWMmVdVvlMHR/AhQwXgQFaa' WHERE username = 'admin';
