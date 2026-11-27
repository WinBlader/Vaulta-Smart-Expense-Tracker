-- ==============================================================================
-- VAULTA - SMART EXPENSE TRACKER
-- Production Supabase Database Schema & Automation Triggers
-- Run this complete script in your Supabase Project: SQL Editor -> New Query -> Run
-- ==============================================================================

-- 1. Create public profiles table linked directly to Supabase auth.users
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT,
    display_name TEXT,
    full_name TEXT,
    email TEXT,
    phone_number TEXT,
    avatar_url TEXT,
    auth_provider TEXT DEFAULT 'Email',
    currency_code TEXT DEFAULT 'INR',
    monthly_budget NUMERIC(14, 2) DEFAULT 0.00,
    initial_balance NUMERIC(14, 2) DEFAULT 0.00,
    privacy_policy_accepted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Ensure newer columns exist if migrating an existing database
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='profiles' AND column_name='display_name') THEN
        ALTER TABLE public.profiles ADD COLUMN display_name TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='profiles' AND column_name='currency_code') THEN
        ALTER TABLE public.profiles ADD COLUMN currency_code TEXT DEFAULT 'INR';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='profiles' AND column_name='monthly_budget') THEN
        ALTER TABLE public.profiles ADD COLUMN monthly_budget NUMERIC(14, 2) DEFAULT 0.00;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='profiles' AND column_name='initial_balance') THEN
        ALTER TABLE public.profiles ADD COLUMN initial_balance NUMERIC(14, 2) DEFAULT 0.00;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='profiles' AND column_name='privacy_policy_accepted') THEN
        ALTER TABLE public.profiles ADD COLUMN privacy_policy_accepted BOOLEAN DEFAULT FALSE;
    END IF;
END $$;

-- 2. Enable Row Level Security (RLS) on the profiles table
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- 3. Policies for profiles
DROP POLICY IF EXISTS "Users can view their own profile" ON public.profiles;
CREATE POLICY "Users can view their own profile"
    ON public.profiles
    FOR SELECT
    USING (auth.uid() = id);

DROP POLICY IF EXISTS "Users can insert their own profile" ON public.profiles;
CREATE POLICY "Users can insert their own profile"
    ON public.profiles
    FOR INSERT
    WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Users can update their own profile" ON public.profiles;
CREATE POLICY "Users can update their own profile"
    ON public.profiles
    FOR UPDATE
    USING (auth.uid() = id);

DROP POLICY IF EXISTS "Users can delete their own profile" ON public.profiles;
CREATE POLICY "Users can delete their own profile"
    ON public.profiles
    FOR DELETE
    USING (auth.uid() = id);

-- 4. Create transactions table for Cloud Backup & Sync
CREATE TABLE IF NOT EXISTS public.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    local_id BIGINT,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    type TEXT NOT NULL DEFAULT 'EXPENSE',
    category TEXT NOT NULL DEFAULT 'General',
    payment_method TEXT,
    note TEXT,
    date_millis BIGINT NOT NULL DEFAULT (extract(epoch from now()) * 1000)::bigint,
    is_credit_owe BOOLEAN DEFAULT FALSE,
    credit_owe_status TEXT,
    credit_direction TEXT,
    counterparty_name TEXT,
    timestamp BIGINT,
    date TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Ensure newer columns exist if migrating an existing database
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='transactions' AND column_name='category') THEN
        ALTER TABLE public.transactions ADD COLUMN category TEXT DEFAULT 'General';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='transactions' AND column_name='payment_method') THEN
        ALTER TABLE public.transactions ADD COLUMN payment_method TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='transactions' AND column_name='note') THEN
        ALTER TABLE public.transactions ADD COLUMN note TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='transactions' AND column_name='date_millis') THEN
        ALTER TABLE public.transactions ADD COLUMN date_millis BIGINT DEFAULT (extract(epoch from now()) * 1000)::bigint;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='transactions' AND column_name='is_credit_owe') THEN
        ALTER TABLE public.transactions ADD COLUMN is_credit_owe BOOLEAN DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='transactions' AND column_name='credit_owe_status') THEN
        ALTER TABLE public.transactions ADD COLUMN credit_owe_status TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='transactions' AND column_name='credit_direction') THEN
        ALTER TABLE public.transactions ADD COLUMN credit_direction TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='transactions' AND column_name='counterparty_name') THEN
        ALTER TABLE public.transactions ADD COLUMN counterparty_name TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='transactions' AND column_name='timestamp') THEN
        ALTER TABLE public.transactions ADD COLUMN timestamp BIGINT;
    END IF;
END $$;

-- 5. Enable Row Level Security (RLS) on transactions
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;

-- 6. Policies for transactions (auth.uid() = user_id)
DROP POLICY IF EXISTS "Users can view their own transactions" ON public.transactions;
CREATE POLICY "Users can view their own transactions"
    ON public.transactions
    FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert their own transactions" ON public.transactions;
CREATE POLICY "Users can insert their own transactions"
    ON public.transactions
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update their own transactions" ON public.transactions;
CREATE POLICY "Users can update their own transactions"
    ON public.transactions
    FOR UPDATE
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can delete their own transactions" ON public.transactions;
CREATE POLICY "Users can delete their own transactions"
    ON public.transactions
    FOR DELETE
    USING (auth.uid() = user_id);

-- 7. Indexes for performant cloud querying
CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON public.transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON public.transactions(user_id, date);

-- 8. Function & Trigger: Automatic profile creation trigger on new user signup
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
        display_name,
        full_name,
        email,
        phone_number,
        auth_provider,
        currency_code,
        monthly_budget,
        privacy_policy_accepted,
        updated_at
    )
    VALUES (
        NEW.id,
        raw_user_name,
        raw_user_name,
        raw_user_name,
        NEW.email,
        raw_phone,
        COALESCE(NEW.raw_app_meta_data->>'provider', 'Email'),
        COALESCE(NEW.raw_user_meta_data->>'currency_code', 'INR'),
        COALESCE((NEW.raw_user_meta_data->>'monthly_budget')::NUMERIC, 0.00),
        COALESCE((NEW.raw_user_meta_data->>'privacy_policy_accepted_v1')::BOOLEAN, FALSE),
        NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        username = EXCLUDED.username,
        display_name = COALESCE(public.profiles.display_name, EXCLUDED.display_name),
        full_name = EXCLUDED.full_name,
        email = EXCLUDED.email,
        phone_number = EXCLUDED.phone_number,
        updated_at = NOW();

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
