-- Add image_url column to vehicles table
ALTER TABLE vehicles
ADD COLUMN image_url VARCHAR(500) DEFAULT NULL;

