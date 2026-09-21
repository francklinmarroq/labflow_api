## Purpose

The techniques an exam template can be run by — ELISA, quimioluminiscencia, aglutinación — held on the template itself rather than retyped on every order. It defines what a method is, which one a template starts an order with, who may administer them, and what renaming or deleting one does to the orders that already used it.

## ADDED Requirements

### Requirement: A method belongs to one exam template and is a name

A method SHALL belong to exactly one exam template and SHALL consist of a name and nothing else. The name SHALL be stored as it was written — that is what gets printed on the report — and SHALL be at most 255 characters; anything longer SHALL be cut rather than refused. Leading and trailing spaces SHALL be dropped and runs of spaces collapsed to one.

Two names SHALL be the same method when they match after removing accents, collapsing spaces and lower-casing. A template SHALL NOT hold two methods that are the same method under that comparison. Two templates SHALL be able to hold a method of the same name independently: they are different methods, of different exams, and neither SHALL affect the other.

A blank name SHALL NOT become a method.

#### Scenario: The same method written two ways on one template

- **WHEN** a template already holds "Quimioluminiscencia" and something names "quimioluminiscencia" or " Quimioluminiscencia "
- **THEN** it resolves to the method already on that template, and the template still holds exactly one

#### Scenario: The same word on two templates

- **WHEN** two different exam templates each hold a method named "ELISA"
- **THEN** each template holds its own, and administering one does not touch the other

#### Scenario: A blank name

- **WHEN** a request would add a method whose name is empty or only spaces
- **THEN** no method is added and nothing else changes

### Requirement: A template has at most one default method

A template SHALL have at most one method marked as its default, and MAY have none. The default is what a new exam of that template starts with; what that means for the order is defined by the `order-test-method` capability.

Marking a method as the default SHALL unmark whichever method was the default before, so the "at most one" holds without the caller having to unmark anything. Removing a template's default status from every method — leaving it with none — SHALL be allowed: a template whose exams are run different ways each time is legitimate.

The default SHALL be reported alongside the template's methods wherever the template is read, so a caller can tell which one a new exam will start with.

#### Scenario: Marking a different method as the default

- **WHEN** a template whose default is "ELISA" has "Quimioluminiscencia" marked as its default
- **THEN** "Quimioluminiscencia" is the default and "ELISA" no longer is, both still on the template

#### Scenario: A template with no default

- **WHEN** a template holds methods but none is marked as the default
- **THEN** that is accepted, and an exam of that template starts with no method

#### Scenario: Reading a template

- **WHEN** a template is read
- **THEN** its methods come with it, and which one is the default is identifiable

### Requirement: The method chosen on an order becomes the template's default

Choosing a method for an exam on an order SHALL mark that method as the default of that exam's template. That is how a laboratory's usual technique settles in: it is recorded once, on the order where it was used, and every exam of that template created afterwards starts with it, until somebody chooses differently on a later order.

Marking the default this way SHALL NOT alter any order already created, nor any report already issued. It changes only what the *next* exams of that template start with.

#### Scenario: The first time a technique is used

- **WHEN** an exam on an order is given the method "ELISA" and its template had no default
- **THEN** "ELISA" is the template's default from then on

#### Scenario: Changing technique on a later order

- **WHEN** an exam on a later order is given "Quimioluminiscencia" while the template's default was "ELISA"
- **THEN** the template's default becomes "Quimioluminiscencia", and the orders that already used "ELISA" still say "ELISA"

### Requirement: Methods are administered where the exam template is

A method SHALL be added, renamed, marked as the default or removed as part of administering the exam template it belongs to, under the same permissions that already govern editing an exam's setup. There SHALL be no separate administration of methods outside that: a method with no template is not a thing.

Adding to a template a method it already has, however spelled, SHALL leave the template with one such method rather than two, and SHALL NOT be an error — it is the same method being named again.

#### Scenario: Setting up an exam's methods

- **WHEN** a user who may edit the exam catalogue saves an exam template with three methods, one of them marked as the default
- **THEN** the template holds the three, with that one as its default

#### Scenario: Without permission to edit the catalogue

- **WHEN** a caller lacking that permission attempts to change a template's methods
- **THEN** the request is refused on authorization and the template's methods are unchanged

#### Scenario: Naming the same method twice in one save

- **WHEN** a template is saved with both "ELISA" and "elisa" among its methods
- **THEN** the template ends up with one such method, not two, and the save is not refused

### Requirement: Renaming a method corrects it everywhere; a method in use cannot be deleted

Renaming a method SHALL apply to every exam that used it, on every order, including orders already reported — a rename is a correction of how a technique is written, and the point of making it is that the reports stop being wrong.

Removing a method from a template SHALL be refused while any exam on any order still names it, and the refusal SHALL say so in a way that identifies how many exams hold it back. The method is printed on reports already issued; making those reports stop saying which technique produced the result is not a correction but a loss. A method no exam names SHALL be removable freely.

Removing the method that was the default SHALL leave the template with no default rather than picking another one.

#### Scenario: Correcting how a technique is written

- **WHEN** a method written "quimioluminicencia" is renamed to "Quimioluminiscencia"
- **THEN** the rename is applied, and every order that used it reports the corrected name

#### Scenario: Deleting a method that orders used

- **WHEN** a template is saved without a method that exams on existing orders name
- **THEN** the save is refused with a business-rule error saying the method is in use, and neither the template nor those orders change

#### Scenario: Deleting an unused method

- **WHEN** a method no exam has ever used is removed from its template
- **THEN** it is removed and nothing else changes

#### Scenario: Deleting the default

- **WHEN** the method marked as the default is removed, and no exam used it
- **THEN** the template is left with no default, and no other method is promoted

### Requirement: Methods typed before the templates held them end up on the templates

Methods recorded as free text on exams of orders before templates held methods SHALL end up as methods of the corresponding template, without anyone re-entering them, and the exams SHALL end up naming them. For each template, every distinct name recorded on its exams SHALL become one method — distinct under the same comparison that makes two spellings one method — and the one used most recently SHALL be marked as that template's default.

An exam whose template cannot be determined SHALL be left exactly as it is and SHALL be reported, so that nothing is guessed and nothing is lost. The migration SHALL be repeatable without creating duplicates or relinking what is linked, SHALL cover every laboratory, and SHALL never prevent the API from starting.

#### Scenario: Text recorded before the change

- **WHEN** the API starts with exams of one template recording "ELISA", "elisa" and, most recently, "Quimioluminiscencia"
- **THEN** that template holds two methods, each exam names the right one, and "Quimioluminiscencia" is the template's default

#### Scenario: An exam with no template

- **WHEN** the migration meets an exam that recorded a method but whose template cannot be determined
- **THEN** that exam is left untouched, no method is invented, and the case is reported

#### Scenario: Starting again

- **WHEN** the API starts again after a successful migration
- **THEN** no method is created, no exam is relinked, and no default changes
