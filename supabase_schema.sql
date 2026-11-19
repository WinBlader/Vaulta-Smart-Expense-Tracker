-- ==============================================================================
-- VAULTA - SMART EXPENSE TRACKER
-- Production Supabase Database Schema & Automation Triggers
-- Run this complete script in your Supabase Project: SQL Editor -> New Query -> Run
-- ==============================================================================

-- 1. Create public profiles table linked directly to Supabase auth.users
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT,
    full_name TEXT,
    email TEXT,
    phone_number TEXT,
    avatar_url TEXT,
    auth_provider TEXT DEFAULT 'Email',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 2. Enable Row Level Security (RLS) on the profiles table
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- 3. Policy: Allow users to view their own profile
DROP POLICY IF EXISTS "Users can view their own profile" ON public.profiles;
CREATE POLICY "Users can view their own profile"
    ON public.profiles
    FOR SELECT
    USING (auth.uid() = id);

-- 4. Policy: Allow users to insert their own profile
DROP POLICY IF EXISTS "Users can insert their own profile" ON public.profiles;
CREATE POLICY "Users can insert their own profile"
    ON public.profiles
    FOR INSERT
    WITH CHECK (auth.uid() = id);

-- 5. Policy: Allow users to update their own profile
DROP POLICY IF EXISTS "Users can update their own profile" ON public.profiles;
CREATE POLICY "Users can update their own profile"
    ON public.profiles
    FOR UPDATE
    USING (auth.uid() = id);

-- 6. Function: Automatic profile creation trigger on new user signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    raw_user_name TEXT;
    raw_phone TEXT;
BEGIN
    raw_user_name := COALESCE(
        NEW.raw_user_meta_data->>'username',
        NEW.raw_user_meta_data->>'full_name',
        NEW.raw_user_meta_data->>'name',
        split_part(NEW.email, '@', 1)
    );

    raw_phone := COALESCE(
        NEW.raw_user_meta_data->>'phone_number',
        NEW.phone,
        ''
    );

    INSERT INTO public.profiles (
        id,
        username,
        full_name,
        email,
        phone_number,
        auth_provider,
        updated_at
    )
    VALUES (
        NEW.id,
        raw_user_name,
        raw_user_name,
        NEW.email,
        raw_phone,
        COALESCE(NEW.raw_app_meta_data->>'provider', 'Email'),
        NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        username = EXCLUDED.username,
        full_name = EXCLUDED.full_name,
        email = EXCLUDED.email,
        phone_number = EXCLUDED.phone_number,
        updated_at = NOW();

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 7. Trigger: Trigger handle_new_user on every auth.users insert
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- 8. Optional: Sync existing users into profiles (if any exist prior to script execution)
INSERT INTO public.profiles (id, username, full_name, email, phone_number, auth_provider, updated_at)
SELECT
    id,
    COALESCE(raw_user_meta_data->>'username', raw_user_meta_data->>'full_name', split_part(email, '@', 1)),
    COALESCE(raw_user_meta_data->>'full_name', raw_user_meta_data->>'name', split_part(email, '@', 1)),
    email,
    COALESCE(raw_user_meta_data->>'phone_number', phone, ''),
    COALESCE(raw_app_meta_data->>'provider', 'Email'),
    NOW()
FROM auth.users
ON CONFLICT (id) DO NOTHING;
